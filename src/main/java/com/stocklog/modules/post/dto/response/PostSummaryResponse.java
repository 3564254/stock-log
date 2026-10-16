package com.stocklog.modules.post.dto.response;

import java.util.List;

/** 피드 목록 한 줄 */
public record PostSummaryResponse(
        Long id,
        String title,
        String preview,
        String authorName,
        String createdAt,
        long likeCount,
        long commentCount,
        long viewCount,
        boolean liked,
        List<StockTagResponse> tags
) {
}
