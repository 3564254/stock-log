package com.stocklog.modules.post.dto.request;

import java.util.List;

/** 글 작성·수정 요청 (JSON) */
public record PostWriteRequest(
        String title,
        String content,
        List<String> stockCodes
) {
}
