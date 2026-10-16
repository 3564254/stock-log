package com.stocklog.modules.post.service;

import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.member.repository.MemberRepository;
import com.stocklog.modules.post.dto.request.CommentWriteRequest;
import com.stocklog.modules.post.dto.response.CommentResponse;
import com.stocklog.modules.post.dto.response.CursorResponse;
import com.stocklog.modules.post.entity.Comment;
import com.stocklog.modules.post.entity.Post;
import com.stocklog.modules.post.repository.CommentRepository;
import com.stocklog.modules.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 댓글·대댓글.
 * 한 페이지 = 쿼리 2번: 1) 댓글 N개 + 작성자  2) 그 댓글들의 대댓글 전부 + 작성자 (parent_id IN)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    public static final int PAGE_SIZE = 20;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final PostService postService;

    public CursorResponse<CommentResponse> getComments(Long postId, Long memberId, Long cursor) {
        Long postAuthorId = postService.getActivePost(postId).getMember().getId();

        long from = (cursor == null || cursor < 0) ? 0L : cursor;
        List<Comment> rows = commentRepository.findRootComments(postId, from, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasNext = rows.size() > PAGE_SIZE;
        List<Comment> roots = hasNext ? rows.subList(0, PAGE_SIZE) : rows;
        if (roots.isEmpty()) {
            return new CursorResponse<>(List.of(), null, false);
        }

        List<Long> rootIds = roots.stream().map(Comment::getId).toList();
        Map<Long, List<CommentResponse>> repliesByParent = commentRepository.findRepliesByParentIdIn(rootIds).stream()
                .filter(Comment::isActive)   // 지워진 대댓글은 보여 줄 필요가 없다
                .collect(Collectors.groupingBy(
                        c -> c.getParent().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(c -> toResponse(c, memberId, postAuthorId, List.of()), Collectors.toList())));

        List<CommentResponse> items = roots.stream()
                .map(c -> toResponse(c, memberId, postAuthorId, repliesByParent.getOrDefault(c.getId(), List.of())))
                // 지워졌고 대댓글도 없는 댓글은 자리만 차지하니 뺀다
                .filter(r -> !r.deleted() || !r.replies().isEmpty())
                .toList();

        Long nextCursor = hasNext ? roots.get(roots.size() - 1).getId() : null;
        return new CursorResponse<>(items, nextCursor, hasNext);
    }

    @Transactional
    public CommentResponse create(Long postId, Long memberId, CommentWriteRequest request) {
        Post post = postService.getActivePost(postId);
        String content = PostService.requireText(request.content(), Comment.CONTENT_MAX, "댓글");

        Comment parent = null;
        if (request.parentId() != null) {
            parent = commentRepository.findWithPostById(request.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            // 다른 글의 댓글 id 를 넣거나, 대댓글에 또 답글을 다는 요청을 막는다
            if (!Objects.equals(parent.getPost().getId(), postId) || parent.isReply() || !parent.isActive()) {
                throw new BusinessException(ErrorCode.INVALID_COMMENT_PARENT);
            }
        }

        Member writer = memberRepository.getReferenceById(memberId);
        Comment saved = commentRepository.save(new Comment(post, writer, parent, content));
        postRepository.increaseCommentCount(postId);

        return toResponse(saved, memberId, post.getMember().getId(), List.of());
    }

    @Transactional
    public void delete(Long commentId, Long memberId) {
        Comment comment = commentRepository.findById(commentId)
                .filter(Comment::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isWrittenBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }
        comment.delete();
        postRepository.decreaseCommentCount(comment.getPost().getId());
    }

    private static CommentResponse toResponse(Comment c, Long memberId, Long postAuthorId, List<CommentResponse> replies) {
        boolean deleted = !c.isActive();
        return new CommentResponse(
                c.getId(),
                c.isReply() ? c.getParent().getId() : null,
                deleted ? null : c.getContent(),
                deleted ? null : c.getMember().getUsername(),
                !deleted && c.isWrittenBy(memberId),
                !deleted && c.isWrittenBy(postAuthorId),
                deleted,
                PostService.format(c.getCreatedDate()),
                replies);
    }
}
