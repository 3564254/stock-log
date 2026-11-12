package com.stocklog.modules.purchase.portfolio.review;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 화면(포트폴리오 점검)이 이미 계산한 규칙 결과. 금액은 넣지 않는다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewFacts(
        String profile,
        String headline,
        List<String> todo,
        List<String> checks,
        List<HoldingFact> holdings
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HoldingFact(String code, String name, String state, Double returnPct, Double weightPct,
                              Double lineGapPct, Boolean longDown, Boolean indexEtf, Boolean profitLine) {}
}
