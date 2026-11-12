package com.stocklog.modules.market.analysis.service.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocklog.modules.market.analysis.dto.EngineInput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 분석기 결과를 "분석기 이름 → 점수" 맵으로 바꾼다 */
public final class EngineScores {

    private EngineScores() {}

    public static final List<String> KEYS = List.of("trend", "momentum", "breadth", "risk", "rates", "liquidity",
            "macro", "credit", "sector", "defensive", "crossAsset", "divergence", "shock");

    public static Map<String, Double> of(EngineInput in) {
        Map<String, Double> m = new HashMap<>();
        if (in == null) return m;
        put(m, "trend", in.trend()); put(m, "momentum", in.momentum()); put(m, "breadth", in.breadth());
        put(m, "risk", in.risk()); put(m, "rates", in.rates()); put(m, "liquidity", in.liquidity());
        put(m, "macro", in.macro()); put(m, "credit", in.credit()); put(m, "sector", in.sector());
        put(m, "defensive", in.defensive()); put(m, "crossAsset", in.crossAsset());
        put(m, "divergence", in.divergence()); put(m, "shock", in.shock());
        return m;
    }

    /** 진단 기록의 EngineInput JSON에서 분석기별 score만 읽는다 */
    public static Map<String, Double> fromJson(ObjectMapper om, String json) {
        Map<String, Double> m = new HashMap<>();
        if (json == null || json.isBlank()) return m;
        try {
            JsonNode root = om.readTree(json);
            for (String k : KEYS) {
                JsonNode v = root.path(k).path("score");
                if (v.isNumber()) m.put(k, v.asDouble());
            }
        } catch (Exception e) {
            return new HashMap<>();
        }
        return m;
    }

    private static void put(Map<String, Double> m, String k, MarketAnalyzerScore s) {
        if (s != null) m.put(k, s.score());
    }
}
