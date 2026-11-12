package com.stocklog.modules.market.analysis.service.allocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 레버리지(레버리지 ETF·신용) 허용 여부 게이트. 상태 없는 순수 계산 */
public final class LeverageGate {

    private LeverageGate() {}

    static final double AXIS = 20;
    static final double SHOCK = -30;
    static final double CREDIT = -30;
    static final int MIN_CONFIDENCE = 60;
    static final double MAX_CASH = 20;
    public static final int MIN_STREAK = 5;
    static final double MAX_SHARE_PCT = 20; // 허용 시에도 주식 투자금 중 레버리지 상품 상한

    public enum Status { ALLOW, CAUTION, AVOID }

    public record Check(String title, boolean pass, String detail, boolean required) {}

    public record LeverageGuide(
            Status status,
            String label,          // 허용 / 보류 / 비추천
            String summary,        // 한 줄 설명
            List<Check> checks,
            int streakDays,        // 필수 조건 연속 충족 일수 (오늘 포함)
            double maxSharePct     // 허용일 때 주식 투자금 중 레버리지 상한 (%), 아니면 0
    ) {
        public String statusName() { return status.name(); }
    }

    /** 필수 조건만 */
    public static boolean requiredPass(Map<String, Double> s) {
        return s.getOrDefault("trend", 0.0) >= AXIS
                && s.getOrDefault("risk", 0.0) >= AXIS
                && s.getOrDefault("shock", 0.0) > SHOCK
                && s.getOrDefault("credit", 0.0) > CREDIT;
    }

    public static LeverageGuide evaluate(Map<String, Double> s, int confidence, double cashPct, int streak) {
        double trend = s.getOrDefault("trend", 0.0), risk = s.getOrDefault("risk", 0.0);
        double shock = s.getOrDefault("shock", 0.0), credit = s.getOrDefault("credit", 0.0);
        double momentum = s.getOrDefault("momentum", 0.0);

        List<Check> c = new ArrayList<>();
        c.add(new Check("상승 추세", trend >= AXIS, String.format("추세 점수 %+.0f (기준 +20 이상)", trend), true));
        c.add(new Check("위험 선호", risk >= AXIS, String.format("위험 점수 %+.0f (기준 +20 이상)", risk), true));
        c.add(new Check("단기 충격 없음", shock > SHOCK, shock > SHOCK ? "5일 급락·VIX 급등 없음" : "5일 급락 또는 VIX 급등 감지", true));
        c.add(new Check("신용 안정", credit > CREDIT, String.format("신용 점수 %+.0f", credit), true));
        c.add(new Check("모멘텀", momentum >= 0, String.format("모멘텀 점수 %+.0f (기준 0 이상)", momentum), false));
        c.add(new Check("지표 신뢰도", confidence >= MIN_CONFIDENCE, String.format("신뢰도 %d (기준 %d 이상)", confidence, MIN_CONFIDENCE), false));
        c.add(new Check("현금 비중", cashPct <= MAX_CASH, String.format("권장 현금 %.0f%% (기준 %.0f%% 이하)", cashPct, MAX_CASH), false));
        c.add(new Check("지속성", streak >= MIN_STREAK, String.format("필수 조건 %d일 연속 (기준 %d일)", streak, MIN_STREAK), false));

        boolean required = c.stream().filter(Check::required).allMatch(Check::pass);
        boolean all = c.stream().allMatch(Check::pass);

        if (!required) {
            String failed = String.join(", ", c.stream().filter(x -> x.required() && !x.pass()).map(Check::title).toList());
            return new LeverageGuide(Status.AVOID, "비추천",
                    "필수 조건 중 " + failed + "을(를) 통과하지 못했어요. 레버리지 상품은 이런 구간에서 지수보다 손실이 크게 쌓이기 쉬워요.",
                    List.copyOf(c), streak, 0);
        }
        if (!all) {
            String failed = String.join(", ", c.stream().filter(x -> !x.pass()).map(Check::title).toList());
            return new LeverageGuide(Status.CAUTION, "보류",
                    "필수 조건은 통과했지만 " + failed + " 조건이 아직이에요. 신규 레버리지는 조건이 갖춰질 때까지 기다리는 편이 안전해요.",
                    List.copyOf(c), streak, 0);
        }
        return new LeverageGuide(Status.ALLOW, "허용",
                String.format("상승 추세와 안정된 심리가 %d일째 이어지고 있어요. 쓰더라도 주식 투자금의 %.0f%% 이내, 단기로, 지수가 20일선을 깨면 정리하는 기준을 정해 두세요.",
                        streak, MAX_SHARE_PCT),
                List.copyOf(c), streak, MAX_SHARE_PCT);
    }
}
