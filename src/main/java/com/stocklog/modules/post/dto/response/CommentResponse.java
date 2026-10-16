package com.stocklog.modules.post.dto.response;

import java.util.List;

/**
 * 댓글. 삭제된 댓글은 deleted = true, content·authorName 을 비워서 내려준다(대댓글은 남는다).
 * byPostAuthor = 글쓴이가 단 댓글("글쓴이" 표시용)
 */
public record CommentResponse(
        Long id,
        Long parentId,
        String content,
        String authorName,
        boolean mine,
        boolean byPostAuthor,
        boolean deleted,
        String createdAt,
        List<CommentResponse> replies
) {
}
