package com.stocklog.modules.post.dto.request;

/** 댓글 작성 요청. parentId 가 있으면 그 댓글의 대댓글 */
public record CommentWriteRequest(
        String content,
        Long parentId
) {
}
