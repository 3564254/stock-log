package com.stocklog.modules.post.dto.response;

import java.util.List;

/**
 * 커서 페이지 응답. 다음 요청에 nextCursor 를 그대로 넘기면 이어서 받는다.
 * hasNext 는 size + 1 개를 조회해서 하나가 더 있으면 true (COUNT 쿼리 없이).
 */
public record CursorResponse<T>(
        List<T> items,
        Long nextCursor,
        boolean hasNext
) {
}
