package com.stocklog.modules.common.finance.model;

public class PhaseConst {
    /** CLIMAX 판정 RSI */
    public static final double CLIMAX_RSI = 75.0;

    /** 볼린저 %B 과열 기준 */
    public static final double PERCENT_B_OVERBOUGHT = 1.0;

    /** 일반 종목 20일선 이격도(%) 경고 */
    public static final double WARNING_DIST_MA20 = 15.0;

    /** 강한 추세 종목 이격도(%) 경고 (더 넉넉하게) */
    public static final double STRONG_TREND_DIST_MA20 = 25.0;

    /** 근접 판정 허용 오차 (비율: 2%) */
    public static final double NEAR_TOLERANCE = 0.02;

    /** WARNING 점수에 반영되는 RSI 기준 */
    public static final double WARNING_RSI = 65.0;

    /** WARNING_ZONE 판정에 필요한 최소 경고 점수 (1~5) */
    public static final int WARNING_MIN_SCORE = 2;

    /** ENTRY_READY 허용 RSI 상한 (과열이면 진입 대기 아님) */
    public static final double ENTRY_MAX_RSI = 70.0;
}
