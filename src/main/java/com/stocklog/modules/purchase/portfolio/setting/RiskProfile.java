package com.stocklog.modules.purchase.portfolio.setting;

/** 투자 성향. 판정(진입·이탈 기준)은 바꾸지 않고 '얼마나 살지'와 보험 규칙만 바꾼다 */
public enum RiskProfile {
    STABLE("안정", 0.005, 0.15, 5, 0.10, 0.10, 1.0, "HOLD", 3.0),
    BASE("기본", 0.010, 0.20, 8, 0.15, 0.10, 2.0, "HALF", 3.0),
    AGGRESSIVE("공격", 0.015, 0.25, 8, 0.20, 0.15, 3.0, "INFO", 4.0);

    private final String label;
    private final double riskPct;      // 종목당 계좌 위험
    private final double maxWeight;    // 종목당 최대 비중
    private final int slots;           // 권장 보유 종목 수
    private final double brakeDd;      // 계좌가 고점 대비 이만큼 빠지면 신규 위험 절반
    private final double wideStop;     // 계획 없이 산 종목: 본전 보호 전 기준선 = min(차트 손절선, 평단 × (1 − 이 값))
    private final double warnRiskPct;  // 기준선에서 정리될 때 계좌 손실이 이 %를 넘으면 '비중 줄이기 검토'
    private final String marketWeak;   // 시장 약세 때: HOLD 신규 보류 권장 · HALF 위험 절반 권장 · INFO 참고만
    private final double trailAtr;     // 계획대로 산 종목: 매수 후 최고 종가 − 이 값 × ATR 아래로 '종가'가 마감하면 다음 날 정리

    RiskProfile(String label, double riskPct, double maxWeight, int slots, double brakeDd, double wideStop, double warnRiskPct, String marketWeak, double trailAtr) {
        this.trailAtr = trailAtr;
        this.label = label; this.riskPct = riskPct; this.maxWeight = maxWeight; this.slots = slots;
        this.brakeDd = brakeDd; this.wideStop = wideStop; this.warnRiskPct = warnRiskPct; this.marketWeak = marketWeak;
    }

    public String getLabel() { return label; }
    public double getRiskPct() { return riskPct; }
    public double getMaxWeight() { return maxWeight; }
    public int getSlots() { return slots; }
    public double getBrakeDd() { return brakeDd; }
    public double getWideStop() { return wideStop; }
    public double getWarnRiskPct() { return warnRiskPct; }
    public String getMarketWeak() { return marketWeak; }
    public double getTrailAtr() { return trailAtr; }

    public static RiskProfile parse(String v) {
        if (v == null) return BASE;
        try { return RiskProfile.valueOf(v.trim().toUpperCase()); } catch (IllegalArgumentException e) { return BASE; }
    }
}
