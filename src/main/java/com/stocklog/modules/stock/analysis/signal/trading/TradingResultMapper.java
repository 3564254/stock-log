package com.stocklog.modules.stock.analysis.signal.trading;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.signal.SignalStrengthBuilder;
import com.stocklog.modules.stock.analysis.signal.level.PriceZone;
import com.stocklog.modules.stock.analysis.signal.level.SignalInputs;

import java.time.LocalDateTime;
import java.util.List;

import static com.stocklog.modules.common.finance.indicator.phase.PhaseConst.*;

/** 내부 모델(EntrySetup 등)을 TradingSignalResult로 바꾼다 */
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

    /** 진입 계획 요약. 계획이 없으면 EntryPlan.none() */
    private static TradingSignalResult.EntryPlan entryPlan(EntrySetup setup, SignalInputs in, List<PriceZone> zones) {
        if (setup == null) return TradingSignalResult.EntryPlan.none();

        PriceZone entry = setup.entry();
        double strength = zones.stream()
                .filter(z -> z.low() <= entry.high() && z.high() >= entry.low())
                .mapToDouble(PriceZone::strength)
                .max().orElse(0.0);
        boolean inZone = in.price() >= entry.low() && in.price() <= entry.high();
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
        // primary: 지금 체결을 가정한 기준가, secondary: 구간 하단
        return new TradingSignalResult.PriceTarget(
                setup.refPrice(), setup.entry().low(), setup.stop(),
                nz(setup.target1()), nz(setup.target2()), trailing, basis, target, targetBasis);
    }

    /** 고정 위험 비율 사이징: 비중(%) = 허용 손실(%) / 손절까지 거리(%) x 100, 상한 적용 */
    private static TradingSignalResult.RiskManagement riskOf(Verdict verdict, EntrySetup setup) {
        if (setup == null) return TradingSignalResult.RiskManagement.zero();

        double riskPerShare = setup.refPrice() - setup.stop();
        double riskPct = riskPerShare / setup.refPrice() * 100;
        double weight = 0;
        double factor = (setup.hasStructuralTarget() ? 1.0 : 0.5)
                * (setup.type() == EntryType.KNEE ? KNEE_WEIGHT_FACTOR : 1.0);   // 무릎: 추세 전환 확정 전이라 소액
        if (verdict == Verdict.ACTIONABLE) {
            weight = Math.min(MAX_POSITION_PCT, RISK_BUDGET_PCT / riskPct * 100) * factor;
            weight = Math.round(weight * 10) / 10.0;
        }
        return new TradingSignalResult.RiskManagement(riskPerShare, riskPct, weight, factor);
    }

    private static double nz(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }
}
