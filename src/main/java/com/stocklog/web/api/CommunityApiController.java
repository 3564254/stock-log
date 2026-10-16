package com.stocklog.web.api;

import com.stocklog.global.annotation.LoginUser;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.post.dto.request.CommentWriteRequest;
import com.stocklog.modules.post.dto.request.PostWriteRequest;
import com.stocklog.modules.post.dto.response.*;
import com.stocklog.modules.post.service.CommentService;
import com.stocklog.modules.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 커뮤니티 API. /api/** 는 LoginCheckInterceptor 가 로그인을 확인하므로 loginMember 는 null 이 아니다.
 * 오류 응답은 CommunityApiExceptionHandler 가 JSON {code, message} 로 바꿔 준다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/community")
public class CommunityApiController {

    private final PostService postService;
    private final CommentService commentService;

    // ── 게시글 ──

    @GetMapping("/posts")
    public CursorResponse<PostSummaryResponse> feed(@LoginUser MemberResponse loginMember,
                                                    @RequestParam(required = false) String stock,
                                                    @RequestParam(required = false) Long cursor,
                                                    @RequestParam(required = false) Integer size) {
        return postService.getFeed(loginMember.getId(), stock, cursor, size);
    }

    @PostMapping("/posts")
    public ResponseEntity<Map<String, Long>> create(@LoginUser MemberResponse loginMember,
                                                    @RequestBody PostWriteRequest request) {
        Long id = postService.create(loginMember.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/posts/{postId}")
    public PostDetailResponse detail(@LoginUser MemberResponse loginMember, @PathVariable Long postId) {
        return postService.getDetail(postId, loginMember.getId());
    }

    @PutMapping("/posts/{postId}")
    public ResponseEntity<Void> update(@LoginUser MemberResponse loginMember,
                                       @PathVariable Long postId,
                                       @RequestBody PostWriteRequest request) {
        postService.update(postId, loginMember.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> delete(@LoginUser MemberResponse loginMember, @PathVariable Long postId) {
        postService.delete(postId, loginMember.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{postId}/like")
    public LikeResponse toggleLike(@LoginUser MemberResponse loginMember, @PathVariable Long postId) {
        return postService.toggleLike(postId, loginMember.getId());
    }

    // ── 댓글 ──

    @GetMapping("/posts/{postId}/comments")
    public CursorResponse<CommentResponse> comments(@LoginUser MemberResponse loginMember,
                                                    @PathVariable Long postId,
                                                    @RequestParam(required = false) Long cursor) {
        return commentService.getComments(postId, loginMember.getId(), cursor);
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(@LoginUser MemberResponse loginMember,
                                                         @PathVariable Long postId,
                                                         @RequestBody CommentWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.create(postId, loginMember.getId(), request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@LoginUser MemberResponse loginMember, @PathVariable Long commentId) {
        commentService.delete(commentId, loginMember.getId());
        return ResponseEntity.noContent().build();
    }

    // ── 글쓰기 모달의 종목 검색 ──

    @GetMapping("/stocks")
    public List<StockTagResponse> searchStocks(@RequestParam(required = false) String keyword) {
        return postService.searchStocks(keyword);
    }
}
