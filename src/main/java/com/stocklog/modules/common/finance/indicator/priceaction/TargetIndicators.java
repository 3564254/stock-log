package com.stocklog.modules.common.finance.indicator.priceaction;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.model.MovingAverages;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.common.finance.model.Pivot;
import com.stocklog.modules.common.finance.model.TargetResult;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class TargetIndicators {

    private static final double ENTRY_SETUP_MAX_ATR = 2.0;
    private static final double MIN_RISK_REWARD = 1.2;
    private static final double HIGH_VOLATILITY_PCT = 7.0;
    private static final double FALLBACK_ATR_RATIO = 0.02;

    public static TargetResult calculateTargets(MarketContext ctx, ChartPhase phase) {
        double cur = ctx.priceSeries().currentPrice();
        if (!Double.isFinite(cur) || cur <= 0) {
            return TargetResult.createFallback();
        }
        if (phase == ChartPhase.INSUFFICIENT_DATA) {
            // 이 phase에 도달했다는 것은 상위 PhaseIndicators.isValid()가 이미 실패했다는 뜻이므로
            // pivot/이평선 등 다른 필드도 신뢰할 수 없다고 보고 계산 자체를 생략한다.
            return TargetResult.createFallback();
        }

        double atr = resolveAtr(ctx, cur);
        MovingAverages ma = ctx.movingAverages();
        Pivot pivot = ctx.pivots().dailyPivot();

        List<Double> upResistances = resistanceLevels(ctx, cur, atr);
        List<Double> downSupports = supportLevels(ctx, cur);
        double bestSupport = downSupports.isEmpty() ? cur - (atr * 0.5) : downSupports.get(0);

        double primaryEntry;
        double secondaryEntry;
        double stopLoss;
        double primaryTarget;
        double secondaryTarget;
        double trailingStop;

        switch (phase) {

            // ================= 상승 국면 (신규 진입 가능) =================

            // 본격 상승 추세: 얕은 눌림에서 공격적으로 추격 매수
            case STRONG_HOLD -> {
                primaryEntry = Math.max(bestSupport, cur - (atr * 1.5));
                secondaryEntry = Math.max(primaryEntry - (atr * 0.8), ma.ma20());
                stopLoss = secondaryEntry - (atr * 0.5);
                trailingStop = Math.max(primaryEntry, cur - (atr * 1.0));
                primaryTarget = !upResistances.isEmpty() ? upResistances.get(0) : cur + (atr * 1.5);
                secondaryTarget = primaryTarget + (atr * 1.0);
            }

            // 저항 돌파 직후: 돌파 레벨(R1)을 새 지지로 보고 되돌림 매수
            case ENTRY_READY -> {
                double retest = pivot.r1() < cur ? pivot.r1() : cur - atr;
                primaryEntry = Math.max(bestSupport, retest);
                secondaryEntry = Math.max(primaryEntry - (atr * 0.6), bestSupport - (atr * 0.3));
                stopLoss = secondaryEntry - (atr * 0.5);
                trailingStop = Math.max(primaryEntry, cur - (atr * 0.8));
                primaryTarget = !upResistances.isEmpty() ? upResistances.get(0) : cur + (atr * 1.2);
                secondaryTarget = primaryTarget + atr;
            }

            // 지지선 재진입: VWAP 근처 거래량 동반 반등을 기준으로 진입
            case RE_ENTRY -> {
                double vwapLevel = vwapOr(ctx, cur - (atr * 1.2));
                primaryEntry = Math.max(bestSupport, vwapLevel);
                secondaryEntry = primaryEntry - (atr * 0.6);
                stopLoss = secondaryEntry - (atr * 0.5);
                trailingStop = Math.max(vwapLevel, cur - atr);
                primaryTarget = !upResistances.isEmpty() ? upResistances.get(0) : cur + atr;
                secondaryTarget = primaryTarget + (atr * 0.8);
            }

            // 변동성 축소(스퀴즈)/방향 미확정: 확률 게임이므로 넓게 분할 진입
            case SETUP -> {
                primaryEntry = Math.max(bestSupport, cur - (atr * ENTRY_SETUP_MAX_ATR));
                double nextSupport = downSupports.size() > 1 ? downSupports.get(1) : primaryEntry - (atr * 0.8);
                secondaryEntry = Math.min(nextSupport, primaryEntry - (atr * 0.5));
                stopLoss = secondaryEntry - (atr * 0.5);
                trailingStop = Math.max(secondaryEntry, cur - atr);
                primaryTarget = !upResistances.isEmpty() ? upResistances.get(0) : cur + (atr * 1.2);
                secondaryTarget = primaryTarget + atr;
            }

            // 매크로 상승 중 MA20 눌림, 거래량 확인 전이라 소극적 소액 진입
            case COOLDOWN -> {
                primaryEntry = Math.max(bestSupport, ma.ma20());
                secondaryEntry = primaryEntry - (atr * 0.8);
                stopLoss = (ma.ma50() > 0 ? ma.ma50() : secondaryEntry) - (atr * 0.5);
                trailingStop = Math.max(ma.ma20(), cur - (atr * 0.8));
                primaryTarget = !upResistances.isEmpty() ? upResistances.get(0) : cur + (atr * 0.8);
                secondaryTarget = primaryTarget + (atr * 0.6);
            }

            // ================= 바닥/반등 국면 (소액 정찰 진입) =================

            // 하락 멈춤 및 다중 바닥: RR 1.3 이상 확보되는 선에서만 정찰 진입
            case RECOVERY_WATCH -> {
                primaryEntry = Math.max(bestSupport, cur - (atr * 2.5));
                secondaryEntry = primaryEntry - (atr * 0.8);
                double structuralLow = ctx.bollingerBand().lower() > 0
                        ? ctx.bollingerBand().lower()
                        : secondaryEntry - atr;
                stopLoss = Math.min(secondaryEntry - (atr * 0.5), structuralLow - (atr * 0.3));
                trailingStop = stopLoss;
                double riskEstimate = primaryEntry - stopLoss;
                double minTargetByRisk = primaryEntry + (riskEstimate * 1.3);
                primaryTarget = !upResistances.isEmpty()
                        ? Math.max(upResistances.get(0), minTargetByRisk)
                        : minTargetByRisk;
                secondaryTarget = primaryTarget + atr;
            }

            // 투매 절정(S3 이하): 극단적 구간이므로 현재가 기준 좁은 진입 + 넓은 손절, RR 1.5 요구
            case CAPITULATION -> {
                primaryEntry = cur - (atr * 1.0);
                secondaryEntry = primaryEntry - (atr * 1.5);
                stopLoss = secondaryEntry - (atr * 1.5);
                trailingStop = stopLoss;
                double riskEstimate = primaryEntry - stopLoss;
                double minTargetByRisk = primaryEntry + (riskEstimate * 1.5);
                primaryTarget = !upResistances.isEmpty()
                        ? Math.max(upResistances.get(0), minTargetByRisk)
                        : minTargetByRisk;
                secondaryTarget = primaryTarget + atr;
            }

            // 하락장 속 단기 반등: 추세 전환 미확인이므로 근접 저항에서 빠르게 익절
            case BEAR_RALLY -> {
                primaryEntry = Math.max(bestSupport, cur - atr);
                secondaryEntry = primaryEntry - (atr * 0.5);
                stopLoss = secondaryEntry - (atr * 0.4);
                trailingStop = stopLoss;
                primaryTarget = !upResistances.isEmpty()
                        ? Math.min(upResistances.get(0), cur + atr)
                        : cur + (atr * 0.8);
                secondaryTarget = primaryTarget;
            }

            // ================= 고점/분산 국면 (신규 진입 비권장) =================

            // 경고: 아직 추세는 살아있으나 과열, 방어선을 좁혀 관찰
            case WARNING_ZONE -> {
                primaryEntry = Math.max(Math.min(cur - atr, ma.ma20()), cur - (atr * 2.0));
                secondaryEntry = primaryEntry - atr;
                stopLoss = (ma.ma50() > 0 ? ma.ma50() : secondaryEntry) - (atr * 0.5);
                trailingStop = Math.max(ma.ma20(), cur - (atr * 0.8));
                primaryTarget = !upResistances.isEmpty() ? upResistances.get(0) : cur + (atr * 0.6);
                secondaryTarget = primaryTarget + (atr * 0.5);
            }

            // 분할 매도: 목표는 근접 저항(빠른 청산 기준), 재진입은 MA50 아래에서나 고려
            case SCALE_OUT -> {
                primaryEntry = Math.max(bestSupport, (ma.ma50() > 0 ? ma.ma50() : cur - (atr * 2.0)));
                secondaryEntry = primaryEntry - atr;
                stopLoss = secondaryEntry - (atr * 0.5);
                trailingStop = Math.max(cur - (atr * 0.5), ma.ma20());
                primaryTarget = !upResistances.isEmpty() ? upResistances.get(0) : cur + (atr * 0.5);
                secondaryTarget = primaryTarget + (atr * 0.3);
            }

            // 오버슈팅(폭등 후 차익실현 임박): 추가 상승보다 급락 리스크가 크므로 타이트한 방어선만 제시
            case CLIMAX -> {
                primaryEntry = cur - (atr * 0.5);
                secondaryEntry = primaryEntry - (atr * 0.5);
                stopLoss = secondaryEntry - (atr * 0.5);
                trailingStop = cur - (atr * 0.3);
                primaryTarget = pivot.r3() > cur ? pivot.r3() : cur; // R3 이미 넘었으면 추가 목표 없음
                secondaryTarget = primaryTarget;
            }

            // ================= 이탈/위험 국면 (진입 없음, 참고 레벨만) =================

            // 하락 추세 확인(S2 상단 유지 중): 다음 지지(S2) 이탈 여부만 감시
            case DOWNTREND_CONFIRMED -> {
                primaryEntry = pivot.s2();
                secondaryEntry = pivot.s3();
                stopLoss = pivot.s3() - (atr * 0.5);
                trailingStop = stopLoss;
                primaryTarget = primaryEntry; // 신규 진입 비권장이므로 목표 없음
                secondaryTarget = primaryEntry;
            }

            // 핵심 지지(S2) 이탈: 이미 손절 트리거된 상태. 신규 진입 없음
            case STOP_LOSS -> {
                primaryEntry = bestSupport;
                secondaryEntry = primaryEntry - atr;
                stopLoss = secondaryEntry - atr;
                trailingStop = stopLoss;
                primaryTarget = primaryEntry;
                secondaryTarget = primaryEntry;
            }

            // 돌파 실패(불트랩): 급락 리스크 우선, 타이트한 방어선만 제시
            case FAKE_OUT -> {
                primaryEntry = cur - (atr * 0.3);
                secondaryEntry = primaryEntry - (atr * 0.3);
                stopLoss = secondaryEntry - (atr * 0.3);
                trailingStop = stopLoss;
                primaryTarget = primaryEntry;
                secondaryTarget = primaryEntry;
            }

            // 역배열 지속: 다음 주요 지지까지 경계, 신규 진입 없음
            case DANGER_ZONE -> {
                primaryEntry = bestSupport;
                secondaryEntry = primaryEntry - atr;
                stopLoss = secondaryEntry - atr;
                trailingStop = stopLoss;
                primaryTarget = primaryEntry;
                secondaryTarget = primaryEntry;
            }

            // 중립 관망: 20일선 회복 여부가 핵심 관찰 포인트
            case WATCH -> {
                primaryEntry = Math.min(ma.ma20(), cur);
                secondaryEntry = primaryEntry - (atr * 0.8);
                stopLoss = secondaryEntry - (atr * 0.5);
                trailingStop = stopLoss;
                primaryTarget = primaryEntry;
                secondaryTarget = primaryEntry;
            }

            default -> {
                return TargetResult.createFallback();
            }
        }

        // 가격 순서 역전 방지 (진입가 <= 현재가, 2차 진입 < 1차 진입, 손절 < 2차 진입)
        primaryEntry = Math.min(primaryEntry, cur);
        secondaryEntry = Math.min(secondaryEntry, primaryEntry - (atr * 0.1));
        stopLoss = Math.min(stopLoss, secondaryEntry - (atr * 0.1));

        // 최소 손절폭 보장 (호가 노이즈로 인한 whipsaw 방어)
        double minStopDistance = Math.max(cur * 0.015, atr * 0.8);
        if ((primaryEntry - stopLoss) < minStopDistance) {
            stopLoss = primaryEntry - minStopDistance;
        }

        double riskPerShare = primaryEntry - stopLoss;
        double reward = primaryTarget - primaryEntry;
        double riskRewardRatio = riskPerShare > 0 ? (reward / riskPerShare) : 0;
        double volatilityRatio = (atr / cur) * 100;

        double recWeight = recommendWeight(phase, primaryEntry, riskPerShare, volatilityRatio, riskRewardRatio);

        return new TargetResult(
                primaryEntry, secondaryEntry, stopLoss,
                primaryTarget, secondaryTarget, trailingStop,
                riskPerShare, recWeight, riskRewardRatio, volatilityRatio
        );
    }

    private static double recommendWeight(
            ChartPhase phase, double primaryEntry, double riskPerShare,
            double volatilityRatio, double riskRewardRatio
    ) {
        double recWeight = riskPerShare > 0
                ? Math.min((2.0 / ((riskPerShare / primaryEntry) * 100)) * 100, 100.0)
                : 0;

        if (volatilityRatio > HIGH_VOLATILITY_PCT) {
            recWeight = recWeight * 0.5;
        }

        double maxWeight = switch (phase) {
            case STRONG_HOLD -> 100.0;
            case ENTRY_READY -> 65.0;
            case RE_ENTRY -> 50.0;
            case SETUP -> 35.0;
            case COOLDOWN -> 15.0;
            case RECOVERY_WATCH -> 20.0;
            case CAPITULATION -> 12.0;
            case BEAR_RALLY -> 8.0;
            case WARNING_ZONE, SCALE_OUT, CLIMAX,
                 DOWNTREND_CONFIRMED, STOP_LOSS, FAKE_OUT,
                 DANGER_ZONE, WATCH, INSUFFICIENT_DATA -> 0.0;
        };

        recWeight = Math.min(recWeight, maxWeight);

        if (riskRewardRatio < MIN_RISK_REWARD) {
            recWeight = 0;
        }
        return recWeight;
    }

    /**
     * ATR을 우선 사용하고, momentum()이 null이거나 ATR이 비정상이면
     * 항상 계산되는 볼린저 표준편차(stdDev20)로 근사한다.
     * 그마저 비정상이면 현재가의 2%를 최후 fallback으로 쓴다.
     */
    private static double resolveAtr(MarketContext ctx, double cur) {
        if (ctx.momentum() != null) {
            double atr = ctx.momentum().atr();
            if (Double.isFinite(atr) && atr > 0) {
                return atr;
            }
        }
        double stdDev = ctx.bollingerBand().stdDev20();
        if (Double.isFinite(stdDev) && stdDev > 0) {
            return stdDev;
        }
        return cur * FALLBACK_ATR_RATIO;
    }

    private static double vwapOr(MarketContext ctx, double fallback) {
        if (ctx.volume() != null && Double.isFinite(ctx.volume().vwap20())) {
            return ctx.volume().vwap20();
        }
        return fallback;
    }

    /** 현재가보다 0.5 ATR 이상 위에 있는 저항 후보를 오름차순으로 반환한다. */
    private static List<Double> resistanceLevels(MarketContext ctx, double cur, double atr) {
        Pivot pivot = ctx.pivots().dailyPivot();

        Stream<Double> base = Stream.of(pivot.r1(), pivot.r2(), pivot.r3(), ctx.bollingerBand().upper());
        Stream<Double> volumeBased = ctx.volume() != null
                ? Stream.of(ctx.volume().pocPrice(), ctx.volume().vwap20())
                : Stream.empty();

        return Stream.concat(base, volumeBased)
                .filter(p -> Double.isFinite(p) && p > cur + (atr * 0.5))
                .sorted()
                .toList();
    }

    /** 현재가 아래에 있는 지지 후보를 현재가에서 가까운 순(내림차순)으로 반환한다. */
    private static List<Double> supportLevels(MarketContext ctx, double cur) {
        Pivot pivot = ctx.pivots().dailyPivot();
        MovingAverages ma = ctx.movingAverages();

        Stream<Double> base = Stream.of(
                pivot.s1(), pivot.s2(), pivot.s3(),
                ctx.bollingerBand().lower(), ma.ma20(), ma.ma50()
        );
        Stream<Double> volumeBased = ctx.volume() != null
                ? Stream.of(ctx.volume().pocPrice(), ctx.volume().vwap20())
                : Stream.empty();

        return Stream.concat(base, volumeBased)
                .filter(p -> Double.isFinite(p) && p < cur)
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}