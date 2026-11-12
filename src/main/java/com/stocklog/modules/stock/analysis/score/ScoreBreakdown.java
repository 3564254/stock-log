package com.stocklog.modules.stock.analysis.score;

/** 종목 점수 구성. 종합 = 밸류(0~40) + 모멘텀(0~30) + 기술(0~30) */
public record ScoreBreakdown(int total, int valuation, int momentum, int technical) {

    public static ScoreBreakdown of(ValuationScore v, MomentumScore m, TechnicalScore t) {
        return new ScoreBreakdown(v.getScore() + m.getScore() + t.getScore(), v.getScore(), m.getScore(), t.getScore());
    }
}
