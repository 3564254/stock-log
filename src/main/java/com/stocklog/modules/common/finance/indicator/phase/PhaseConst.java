package com.stocklog.modules.common.finance.indicator.phase;

public final class PhaseConst {
    public static final double RECLAIM_MAX_DIST_MA20  = 1.5;
    public static final double PULLBACK_MIN_DIST_MA20 = -1.5;
    public static final double PULLBACK_MAX_DIST_MA20 = 2.0;
    public static final double BREAKOUT_VOL_RATIO     = 1.5;
    public static final double CLUSTER_ATR         = 0.35; // 이 간격 이내의 레벨은 한 구간으로 묶는다
    public static final double MIN_ZONE_WIDTH_ATR  = 0.20; // 레벨이 하나뿐일 때의 최소 구간 폭
    public static final double MIN_SUPPORT_STRENGTH = 3.0; // 눌림 진입에 쓸 지지 구간의 최소 강도
    public static final double MIN_TARGET_STRENGTH  = 2.0; // 목표로 인정할 저항 구간의 최소 강도
    public static final double ENTRY_MAX_DIST_ATR  = 1.0;  // 현재가와 지지 구간 상단의 최대 거리
    public static final double ENTRY_BAND_ATR      = 0.30; // 돌파/탈환 진입 구간 폭
    public static final double STOP_BUFFER_ATR     = 0.40; // 구조 하단 아래 손절 버퍼
    // 비용 포함 포트폴리오 시뮬레이션: 하한을 1.5~3.5 어느 값으로 올려도
    public static final double MIN_RR              = 2.0;
    public static final double KNEE_BAND_ATR       = 0.50; // 무릎 진입 구간 폭 (회복한 5일선부터)
    public static final double KNEE_MAX_RISK_ATR   = 3.0;  // 바닥(손절)에서 이보다 멀리 올라왔으면 '무릎'이 아니다
    public static final double KNEE_WEIGHT_FACTOR  = 0.5;  // 무릎 진입 비중 = 일반 진입 비중의 절반 (추세 전환 확정 전이므로)
    public static final double CHOP_ATR            = 0.15; // 오늘 넘나든 구간 경계에서 현재가가 이 폭 이내면 '경계 확인 중'
    public static final double CHOP_CROSS_ATR      = 0.10; // '넘나들었다'로 보려면 경계 위아래로 각각 이만큼은 가 봤어야 한다
    public static final double RISK_BUDGET_PCT     = 1.0;  // 거래당 허용 손실 (포트폴리오 대비 %)
    public static final double MAX_POSITION_PCT    = 20.0; // 단일 종목 비중 상한 (%)
    public static final double BASE_PULLBACK = 20;
    public static final double BASE_BREAKOUT = 18;
    public static final double BASE_RECLAIM  = 15;
    public static final double BASE_KNEE     = 10;
    public static final double CLIMAX_RSI = 75.0;
    public static final double PERCENT_B_OVERBOUGHT = 1.0;
    public static final double WARNING_DIST_MA20 = 15.0;
    public static final double STRONG_TREND_DIST_MA20 = 25.0;
    public static final double NEAR_TOLERANCE = 0.02;
    public static final double WARNING_RSI = 65.0;
    public static final int WARNING_MIN_SCORE = 2;
    public static final double ENTRY_MAX_RSI = 70.0;
}
