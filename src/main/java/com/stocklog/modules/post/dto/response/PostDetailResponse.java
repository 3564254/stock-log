package com.stocklog.modules.post.dto.response;

import java.util.List;

/** 글 상세. mine = 로그인한 사람이 쓴 글(수정·삭제 버튼 노출용, 실제 권한 검사는 서버에서 다시 한다) */
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
