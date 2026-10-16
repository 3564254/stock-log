package com.stocklog.modules.post.dto.request;

import java.util.List;

/**
 * 글 작성·수정 요청 (JSON).
 * stockCodes: 붙일 종목 코드 목록. 수정 때 null 이면 태그는 그대로 두고, 빈 목록이면 태그를 모두 뗀다.
 */
public record PostWriteRequest(
        String title,
        String content,
        List<String> stockCodes
) {
}
