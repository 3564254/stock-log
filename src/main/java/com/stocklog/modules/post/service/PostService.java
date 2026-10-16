package com.stocklog.modules.post.service;

import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.member.repository.MemberRepository;
import com.stocklog.modules.post.dto.request.PostWriteRequest;
import com.stocklog.modules.post.dto.response.*;
import com.stocklog.modules.post.entity.Post;
import com.stocklog.modules.post.entity.PostLike;
import com.stocklog.modules.post.entity.PostStatus;
import com.stocklog.modules.post.entity.PostStockTag;
import com.stocklog.modules.post.repository.PostLikeRepository;
import com.stocklog.modules.post.repository.PostRepository;
import com.stocklog.modules.post.repository.PostStockTagRepository;
import com.stocklog.modules.stock.stock.Stock;
import com.stocklog.modules.stock.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 커뮤니티 게시글: 작성·수정·삭제, 피드(전체/종목별), 상세, 좋아요.
 *
 * 피드 한 페이지 = 쿼리 3번으로 고정 (글 수와 무관)
 *   1) 글 + 작성자 (join fetch)   2) 그 글들의 태그 + 종목 (IN)   3) 내가 좋아요 누른 글 id (IN)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 140;

    private final PostRepository postRepository;
    private final PostStockTagRepository tagRepository;
    private final PostLikeRepository likeRepository;
    private final MemberRepository memberRepository;
    private final StockRepository stockRepository;

    // ───────────────────────── 작성 · 수정 · 삭제 ─────────────────────────

    @Transactional
    public Long create(Long memberId, PostWriteRequest request) {
        String title = requireText(request.title(), Post.TITLE_MAX, "제목");
        String content = requireText(request.content(), Post.CONTENT_MAX, "내용");
        List<Stock> stocks = resolveStocks(request.stockCodes());

        // 작성자는 프록시만 — 회원 SELECT 없이 member_id 만 채운다
        Member writer = memberRepository.getReferenceById(memberId);
        Post post = postRepository.save(new Post(writer, title, content));
        saveTags(post, stocks);
        return post.getId();
    }

    @Transactional
    public void update(Long postId, Long memberId, PostWriteRequest request) {
        Post post = getOwnedActivePost(postId, memberId);
        String title = requireText(request.title(), Post.TITLE_MAX, "제목");
        String content = requireText(request.content(), Post.CONTENT_MAX, "내용");
        post.update(title, content);

        if (request.stockCodes() != null) {
            List<Stock> stocks = resolveStocks(request.stockCodes());
            tagRepository.deleteAllByPostId(postId);
            saveTags(post, stocks);
        }
    }

    @Transactional
    public void delete(Long postId, Long memberId) {
        getOwnedActivePost(postId, memberId).delete();
    }

    // ───────────────────────── 조회 ─────────────────────────

    /**
     * @param stockCode null 이면 전체 피드, 있으면 그 종목이 태그된 글만
     * @param cursor    이전 응답의 nextCursor. 첫 페이지는 null
     */
    public CursorResponse<PostSummaryResponse> getFeed(Long memberId, String stockCode, Long cursor, Integer size) {
        int pageSize = clampSize(size);
        long from = (cursor == null || cursor <= 0) ? Long.MAX_VALUE : cursor;
        PageRequest limit = PageRequest.of(0, pageSize + 1); // 하나 더 읽어서 다음 페이지 유무 판단

        String code = normalizeCode(stockCode);
        List<Post> rows = (code == null)
                ? postRepository.findFeed(PostStatus.ACTIVE, from, limit)
                : postRepository.findFeedByStock(code, PostStatus.ACTIVE, from, limit);

        boolean hasNext = rows.size() > pageSize;
        List<Post> posts = hasNext ? rows.subList(0, pageSize) : rows;
        if (posts.isEmpty()) {
            return new CursorResponse<>(List.of(), null, false);
        }

        List<Long> ids = posts.stream().map(Post::getId).toList();
        Map<Long, List<StockTagResponse>> tags = findTags(ids);
        Set<Long> liked = (memberId == null) ? Set.of() : new HashSet<>(likeRepository.findLikedPostIds(memberId, ids));

        List<PostSummaryResponse> items = posts.stream()
                .map(p -> new PostSummaryResponse(
                        p.getId(),
                        p.getTitle(),
                        preview(p.getContent()),
                        p.getMember().getUsername(),
                        format(p.getCreatedDate()),
                        p.getLikeCount(),
                        p.getCommentCount(),
                        p.getViewCount(),
                        liked.contains(p.getId()),
                        tags.getOrDefault(p.getId(), List.of())))
                .toList();

        Long nextCursor = hasNext ? posts.get(posts.size() - 1).getId() : null;
        return new CursorResponse<>(items, nextCursor, hasNext);
    }

    /** 상세 조회. 조회수를 먼저 올린 뒤 읽어서 방금 올린 값이 보이게 한다 */
    @Transactional
    public PostDetailResponse getDetail(Long postId, Long memberId) {
        postRepository.increaseViewCount(postId, PostStatus.ACTIVE);
        Post post = postRepository.findWithMemberById(postId)
                .filter(Post::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        List<StockTagResponse> tags = findTags(List.of(postId)).getOrDefault(postId, List.of());
        boolean liked = memberId != null && likeRepository.existsByPostIdAndMemberId(postId, memberId);

        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getMember().getUsername(),
                post.isWrittenBy(memberId),
                format(post.getCreatedDate()),
                isEdited(post.getCreatedDate(), post.getLastModifiedDate()),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                liked,
                tags);
    }

    /** 종목 필터 칩에 이름을 띄우기 위한 조회. 없는 코드면 empty */
    public Optional<StockTagResponse> findStockTag(String stockCode) {
        String code = normalizeCode(stockCode);
        if (code == null) return Optional.empty();
        return stockRepository.findById(code).map(StockTagResponse::from);
    }

    /** 글쓰기 모달의 종목 검색 (시세 조회 없이 이름·코드만) */
    public List<StockTagResponse> searchStocks(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return stockRepository.findByKeyword(keyword.trim(), PageRequest.of(0, 8))
                .map(StockTagResponse::from)
                .getContent();
    }

    // ───────────────────────── 좋아요 ─────────────────────────

    /**
     * 좋아요 토글.
     * 1) 먼저 DELETE 를 해 보고 지운 행이 있으면 "취소"
     * 2) 없으면 INSERT 해서 "좋아요" — 같은 사람이 동시에 두 번 눌러 둘 다 INSERT 로 오면
     *    uk_post_member 때문에 하나는 DataIntegrityViolationException 으로 실패한다(→ API 에서 409).
     * 카운트는 UPDATE ... SET like_count = like_count ± 1 로 DB 에서 바로 바꾼다.
     */
    @Transactional
    public LikeResponse toggleLike(Long postId, Long memberId) {
        Post post = getActivePost(postId);

        boolean liked;
        if (likeRepository.deleteByPostIdAndMemberId(postId, memberId) > 0) {
            postRepository.decreaseLikeCount(postId);
            liked = false;
        } else {
            likeRepository.saveAndFlush(new PostLike(post, memberRepository.getReferenceById(memberId)));
            postRepository.increaseLikeCount(postId);
            liked = true;
        }
        return new LikeResponse(liked, postRepository.findLikeCount(postId));
    }

    // ───────────────────────── 내부 ─────────────────────────

    /** 삭제되지 않은 글을 가져온다 (CommentService 도 사용) */
    public Post getActivePost(Long postId) {
        return postRepository.findById(postId)
                .filter(Post::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private Post getOwnedActivePost(Long postId, Long memberId) {
        Post post = getActivePost(postId);
        if (!post.isWrittenBy(memberId)) {
            // 남의 글 id 를 넣어 수정·삭제를 시도하는 경우(IDOR) — 화면에 버튼이 없어도 API 는 직접 호출될 수 있다
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }
        return post;
    }

    /** 코드 정리(공백·대소문자·중복) → 개수 제한 → 실제 있는 종목인지 확인. 입력 순서를 유지한다 */
    private List<Stock> resolveStocks(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) return List.of();

        List<String> codes = rawCodes.stream()
                .map(PostService::normalizeCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (codes.size() > PostStockTag.MAX_PER_POST) {
            throw new BusinessException(ErrorCode.TOO_MANY_STOCK_TAGS);
        }
        if (codes.isEmpty()) return List.of();

        Map<String, Stock> found = stockRepository.findAllById(codes).stream()
                .collect(Collectors.toMap(Stock::getCode, s -> s));
        List<Stock> result = new ArrayList<>(codes.size());
        for (String code : codes) {
            Stock stock = found.get(code);
            if (stock == null) {
                throw new BusinessException(ErrorCode.STOCK_NOT_FOUND, "존재하지 않는 종목 코드: " + code);
            }
            result.add(stock);
        }
        return result;
    }

    private void saveTags(Post post, List<Stock> stocks) {
        if (stocks.isEmpty()) return;
        tagRepository.saveAll(stocks.stream().map(s -> new PostStockTag(post, s)).toList());
    }

    private Map<Long, List<StockTagResponse>> findTags(Collection<Long> postIds) {
        return tagRepository.findAllWithStockByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(
                        t -> t.getPost().getId(),   // 프록시 id 접근이라 post 를 다시 읽지 않는다
                        LinkedHashMap::new,
                        Collectors.mapping(t -> StockTagResponse.from(t.getStock()), Collectors.toList())));
    }

    static String normalizeCode(String code) {
        if (code == null) return null;
        String c = code.trim().toUpperCase(Locale.ROOT);
        return c.isEmpty() ? null : c;
    }

    static String requireText(String value, int max, String label) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, label + "을(를) 입력해 주세요.");
        }
        if (v.length() > max) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, label + "은(는) " + max + "자까지 쓸 수 있어요.");
        }
        return v;
    }

    private static int clampSize(Integer size) {
        if (size == null || size <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String preview(String content) {
        if (content == null) return "";
        String oneLine = content.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= PREVIEW_LENGTH ? oneLine : oneLine.substring(0, PREVIEW_LENGTH) + "…";
    }

    static String format(LocalDateTime time) {
        return time == null ? null : time.withNano(0).toString(); // 2026-10-15T20:53:00 → 화면에서 "3분 전" 으로
    }

    private static boolean isEdited(LocalDateTime created, LocalDateTime modified) {
        return created != null && modified != null && modified.isAfter(created.plusSeconds(1));
    }
}
