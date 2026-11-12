package com.stocklog.modules.stock.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** AI 리포트 본문. AI가 JSON으로 준 것을 그대로 담는다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockAiReport(
        String headline,
        List<String> tags,
        List<String> situation,
        Scenario up,
        Scenario down,
        List<String> actions,
        List<String> risks,
        String raw
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scenario(String when, String then) {}

    public static StockAiReport rawOnly(String text) {
        return new StockAiReport(null, List.of(), List.of(), null, null, List.of(), List.of(), text);
    }
}
