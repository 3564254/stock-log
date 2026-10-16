package com.stocklog.modules.post.dto.response;

public record LikeResponse(
        boolean liked,
        long likeCount
) {
}
