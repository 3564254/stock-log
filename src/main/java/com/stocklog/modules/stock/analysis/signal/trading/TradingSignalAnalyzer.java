package com.stocklog.modules.stock.analysis.signal.trading;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.indicator.phase.PhaseIndicators;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.indicator.IndicatorSignals;
import com.stocklog.modules.stock.analysis.phase.PhaseSignals;
import com.stocklog.modules.stock.analysis.signal.SignalStrengthBuilder;
import com.stocklog.modules.stock.analysis.signal.level.PriceZone;
import com.stocklog.modules.stock.analysis.signal.level.SignalInputs;
import com.stocklog.modules.stock.analysis.signal.level.ZoneBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 타점 엔진 (오케스트레이터).
 *
 * 흐름: Phase(PhaseIndicators) -> 진입 유형 -> 가격 구간(Zone) -> 진입 구간 -> 구조적 손절
 *       -> 구조적 목표 -> RR(마지막 feasibility 체크) -> 가중치
 *
 * 세부 책임: 진입 계획 {@link EntryPlanner} / 문구·가중치 {@link EntrySignalText} / 결과 변환 {@link TradingResultMapper}
 * ATR은 "진입가를 정하는 근거"가 아니라 구간 클러스터링 폭, 손절 버퍼, 추격 여부 판단에만 쓴다.
 */
@Component
public class TradingSignalAnalyzer {

    public TradingSignalResult analyze(String symbol, MarketContext ctx) {
        ChartPhase phase = PhaseIndicators.determinePhase(ctx);
        if (phase == ChartPhase.INSUFFICIENT_DATA) {
            return TradingSignalResult.createFallback(symbol, "INSUFFICIENT_DATA");
        }

        SignalStrengthBuilder builder = new SignalStrengthBuilder();
        IndicatorSignals.apply(ctx, builder);
        PhaseSignals.addPhaseSpecificSignals(phase, ctx, builder);

        SignalInputs in = SignalInputs.from(ctx);
        boolean atrOk = Double.isFinite(in.atr()) && in.atr() > 0; // ATR 없이는 구간/손절 계산 불가
        List<PriceZone> zones = atrOk ? ZoneBuilder.build(in) : List.of();

        EntrySetup setup = null;
        Verdict verdict = Verdict.NO_SETUP;
        if (atrOk && EntryPlanner.isPlannablePhase(phase)) {
            EntryType type = PhaseIndicators.classifyEntryType(ctx, phase);
            if (EntryPlanner.isPlannableType(type)) {
                setup = EntryPlanner.build(type, in, zones).orElse(null);
                if (setup != null) {
                    verdict = EntryPlanner.judge(type, in, setup);
                    EntrySignalText.report(verdict, setup, builder);
                }
            }
        }

        if (setup == null && atrOk && EntryPlanner.isReferencePhase(phase)) {
            setup = EntryPlanner.reference(in, zones).orElse(null);
        }

        if (atrOk && verdict != Verdict.ACTIONABLE) {
            EntrySignalText.explain(in, zones, ctx, builder); // 진입 신호가 없어도 "어디를 보고 무엇을 기다릴지"는 알려준다
        }

        // 보유 기준 손절선: 진입 계획이 없어도(하락 국면이어도) 항상 낸다
        EntryPlanner.HoldingStop hold = atrOk ? EntryPlanner.holdingStop(in, zones) : EntryPlanner.HoldingStop.NONE;
        // 보유 기준 익절 목표: 현재가 위 가장 가까운 저항 (없으면 NONE)
        EntryPlanner.HoldingTarget holdTarget = atrOk ? EntryPlanner.holdingTarget(in, zones) : EntryPlanner.HoldingTarget.NONE;
        TradingSignalResult.Narrative narrative = TradingNarrative.build(phase, ctx, verdict, setup);
        return TradingResultMapper.toResult(symbol, phase, ctx, verdict, setup, in, zones,
                narrative, hold, holdTarget, builder.build());
    }
}
