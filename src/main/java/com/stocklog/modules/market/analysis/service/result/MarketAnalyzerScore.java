package com.stocklog.modules.market.analysis.service.result;

// 각 Analyzer가 반환할 표준 객체
public record MarketAnalyzerScore(
        String analyzerName,
        double score,         // -100.0 ~ 100.0
        ScoreCategory category,
        Object rawDetails     // 세부 분석 데이터 (MovingAverage, Slope 등)
) {
    public static ScoreCategory categorize(double score) {
        if (score >= 80) return ScoreCategory.STRONG_POSITIVE;
        if (score >= 40) return ScoreCategory.POSITIVE;
        if (score > -40) return ScoreCategory.NEUTRAL;
        if (score > -80) return ScoreCategory.NEGATIVE;
        return ScoreCategory.STRONG_NEGATIVE;
    }
}
