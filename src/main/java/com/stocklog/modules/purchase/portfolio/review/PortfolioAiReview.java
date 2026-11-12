package com.stocklog.modules.purchase.portfolio.review;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 포트폴리오 AI 주간 점검 본문. 판정은 앱 규칙이 하고, AI는 규칙 결과를 정리만 한다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortfolioAiReview(
        String headline,
        List<String> tags,
        List<String> changes,
        List<String> todo,
        List<Watch> watch,
        List<String> schedule,
        String raw
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Watch(String name, String why) {}

    public static PortfolioAiReview rawOnly(String text) {
        return new PortfolioAiReview(null, List.of(), List.of(), List.of(), List.of(), List.of(), text);
    }
}
