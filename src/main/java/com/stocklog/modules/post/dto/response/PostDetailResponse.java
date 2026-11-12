package com.stocklog.modules.post.dto.response;

import java.util.List;

/** 글 상세. mine = 로그인한 사람이 쓴 글 */
public record PostDetailResponse(
        Long id,
        String title,
        String content,
        String authorName,
        boolean mine,
        String createdAt,
        boolean edited,
        long likeCount,
        long commentCount,
        long viewCount,
        boolean liked,
        List<StockTagResponse> tags
) {
}
