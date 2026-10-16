package com.stocklog.modules.stock.analysis.signal.trading;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.signal.SignalStrengthBuilder;
import com.stocklog.modules.stock.analysis.signal.level.PriceZone;
import com.stocklog.modules.stock.analysis.signal.level.SignalInputs;

import java.time.LocalDateTime;
import java.util.List;

import static com.stocklog.modules.common.finance.indicator.phase.PhaseConst.*;

/** 내부 모델(EntrySetup 등)을 TradingSignalResult로 바꾼다. DTO는 "없음"을 0.0으로 표현하므로 NaN은 0.0으로 변환한다. */
public final class TradingResultMapper {

    private TradingResultMapper() {}

    public static TradingSignalResult toResult(String symbol, ChartPhase phase, MarketContext ctx,
                                               Verdict verdict, EntrySetup setup,
                                               SignalInputs in, List<PriceZone> zones,
                                               TradingSignalResult.Narrative narrative, EntryPlanner.HoldingStop hold,
                                               EntryPlanner.HoldingTarget holdTarget,
                                               SignalStrengthBuilder.SignalResult r) {
        return new TradingSignalResult(
                symbol != null ? symbol : "UNKNOWN",
                new TradingSignalResult.SignalStrength(
                        r.buySignals(), r.sellSignals(), r.watchSignals(),
                        r.buyStrength(), r.sellStrength()),
                new TradingSignalResult.ChartAnalysis(phase, ctx),
                entryPlan(setup, in, zones),
                narrative,
                priceTarget(setup, hold, holdTarget),
                riskOf(verdict, setup),
                verdict.name(),
                LocalDateTime.now());
    }

    /**
     * 진입 계획 요약. 계획이 없으면 EntryPlan.none().
     * - zoneStrength: 진입 구간과 겹치는 레벨 클러스터의 최대 강도 (돌파/탈환은 해당 레벨의 합산 가중치)
     * - riskReward: 구조적 목표가 없으면 0.0 (산출 불가)
     * - triggerConfirmed: 현재가가 구간 안이고 당일 양봉이 확인된 경우만 true.
     *   시가를 모르면(NaN) false다. judge()는 이 경우 트리거를 막지 않으므로 ACTIONABLE이어도 false일 수 있다.
     */
    private static TradingSignalResult.EntryPlan entryPlan(EntrySetup setup, SignalInputs in, List<PriceZone> zones) {
        if (setup == null) return TradingSignalResult.EntryPlan.none();

        PriceZone entry = setup.entry();
        double strength = zones.stream()
                .filter(z -> z.low() <= entry.high() && z.high() >= entry.low())
                .mapToDouble(PriceZone::strength)
                .max().orElse(0.0);
        boolean inZone = in.price() <= entry.high();
        boolean trigger = inZone && Double.isFinite(in.open()) && in.price() > in.open();

        return new TradingSignalResult.EntryPlan(
                setup.type(), entry.low(), entry.high(), strength, nz(setup.rr()), trigger);
    }

    private static TradingSignalResult.PriceTarget priceTarget(EntrySetup setup, EntryPlanner.HoldingStop hold,
                                                               EntryPlanner.HoldingTarget holdTarget) {
        double trailing = hold != null ? hold.price() : 0.0;
        String basis = hold != null ? hold.basis() : "";
        double target = holdTarget != null ? holdTarget.price() : 0.0;
        String targetBasis = holdTarget != null ? holdTarget.basis() : "";
        if (setup == null) {
            return new TradingSignalResult.PriceTarget(0, 0, 0, 0, 0, trailing, basis, target, targetBasis);
        }
        // primary: 지금 체결을 가정한 기준가(구간 안이면 현재가, 위면 구간 상단), secondary: 구간 하단
        return new TradingSignalResult.PriceTarget(
                setup.refPrice(), setup.entry().low(), setup.stop(),
                nz(setup.target1()), nz(setup.target2()), trailing, basis, target, targetBasis);
    }

    /**
     * 고정 위험 비율 사이징: 비중(%) = 허용 손실(%) / 손절까지 거리(%) x 100, 상한 적용.
     * 비중은 ACTIONABLE일 때만 산출하며, 구조적 목표가 없으면 절반만 반영한다.
     */
    private static TradingSignalResult.RiskManagement riskOf(Verdict verdict, EntrySetup setup) {
        if (setup == null) return TradingSignalResult.RiskManagement.zero();

        double riskPerShare = setup.refPrice() - setup.stop();
        double riskPct = riskPerShare / setup.refPrice() * 100;
        double weight = 0;
        if (verdict == Verdict.ACTIONABLE) {
            weight = Math.min(MAX_POSITION_PCT, RISK_BUDGET_PCT / riskPct * 100);
            if (!setup.hasStructuralTarget()) weight *= 0.5;
            weight = Math.round(weight * 10) / 10.0;
        }
        return new TradingSignalResult.RiskManagement(riskPerShare, riskPct, weight);
    }

    private static double nz(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }
}
