package com.stocklog.modules.market.analysis.service.allocation;

import com.stocklog.modules.market.analysis.service.result.MarketRegimeEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 시장 상태 → 권장 현금 비중 */
public final class CashAllocationCalculator {

    private CashAllocationCalculator() {}

    static final double NEUTRAL = 35.0;
    static final double SLOPE = 0.3;
    static final double MIN = 5.0;
    static final double MAX = 70.0;
    static final double STEP = 5.0;
    static final double MAX_DAILY_CHANGE = 10.0;
    static final double MAX_DAILY_RISE_ON_SHOCK = 20.0;

    static final double SHOCK_TRIGGER = -30;      // 단기 충격 점수: 0 / -30 / -50 / -80
    static final double CREDIT_STRESS = -30;
    static final double AXIS = 20;                // 시장 엔진의 추세·위험 축 경계 (±20)
    static final int LOW_CONFIDENCE = 40;

    /** 현금 비중을 움직인 이유 하나. points는 %p */
    public record Reason(String title, String detail, double points, boolean base) {}

    /** 하루치 계산 결과 (하루 변화 제한 전) */
    public record Raw(double coreScore, int confidence, double target, boolean shock, List<Reason> reasons) {}

    public static Raw raw(Map<String, Double> s) {
        double core = MarketRegimeEngine.coreScore(s);
        int confidence = MarketRegimeEngine.confidence(s, core);
        List<Reason> reasons = new ArrayList<>();

        double base = NEUTRAL - SLOPE * core;
        reasons.add(new Reason("기본", String.format("시장 점수 %+.0f", core), base, true));
        double v = base;
        // 가산분까지 절반으로 깎였다
        if (confidence < LOW_CONFIDENCE) {
            double pull = (NEUTRAL - v) / 2;
            if (Math.abs(pull) >= 1) {
                v += add(reasons, "신뢰도 낮음", "지표 방향이 엇갈려 기본값을 중립(35%) 쪽으로 당겼어요", pull);
            }
        }

        boolean shock = s.getOrDefault("shock", 0.0) <= SHOCK_TRIGGER;
        if (shock) {
            v += add(reasons, "단기 충격", "5일 급락 또는 VIX 급등이 감지됐어요", 10);
        }
        if (s.getOrDefault("credit", 0.0) <= CREDIT_STRESS) {
            v += add(reasons, "신용 불안", "회사채 등 신용 시장 지표가 나빠요", 5);
        }
        if (s.getOrDefault("trend", 0.0) >= AXIS && s.getOrDefault("risk", 0.0) <= -AXIS) {
            v += add(reasons, "추세와 심리 엇갈림", "상승 추세지만 위험 회피 신호가 강해요", 5);
        }
        return new Raw(core, confidence, snap(v), shock, List.copyOf(reasons));
    }

    /** 하루 변화 제한 (전날 값이 없으면 그대로). 단기 충격이 있는 날은 늘리는 폭을 +20%p까지 허용 */
    public static double limit(Raw raw, Double previous) {
        if (previous == null) return raw.target();
        double up = raw.shock() ? MAX_DAILY_RISE_ON_SHOCK : MAX_DAILY_CHANGE;
        return snap(Math.max(previous - MAX_DAILY_CHANGE, Math.min(previous + up, raw.target())));
    }

    static double snap(double v) {
        double r = Math.round(v / STEP) * STEP;
        return Math.max(MIN, Math.min(MAX, r));
    }

    private static double add(List<Reason> reasons, String title, String detail, double points) {
        reasons.add(new Reason(title, detail, points, false));
        return points;
    }

    /** 화면용 성격 구분 */
    public static String stanceOf(double pct) {
        if (pct <= 15) return "적극";
        if (pct <= 35) return "중립";
        if (pct <= 50) return "방어";
        return "매우 방어";
    }
}
