package com.stocklog.modules.post.dto.response;

import java.util.List;

/** 커서 페이지 응답. 다음 요청에 nextCursor 를 그대로 넘기면 이어서 받는다 */
public record CursorResponse<T>(
        List<T> items,
        Long nextCursor,
        boolean hasNext
) {
}
