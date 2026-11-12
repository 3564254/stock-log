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

/** 타점 엔진 (오케스트레이터) */
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
            EntryType classified = PhaseIndicators.classifyEntryType(ctx, phase);
            boolean longDown = belowLongTrend(ctx);
            if (longDown) {
                builder.addWatchSignal("📉 [장기 추세 아래] 주가가 40주선(약 200일선) 아래라 눌림목·돌파 진입은 보류합니다. 긴 하락 중의 짧은 반등일 수 있어요");
            }
            for (EntryType type : EntryPlanner.candidateTypes(phase, classified)) {
                if (longDown && (type == EntryType.PULLBACK || type == EntryType.BREAKOUT)) continue;
                setup = EntryPlanner.build(type, in, zones).orElse(null);
                if (setup != null) {
                    verdict = EntryPlanner.judge(type, in, setup);
                    EntrySignalText.report(verdict, setup, builder);
                    break;
                }
            }
        }

        // 계획이 없어도, 오늘 강한 지지 구간 경계를 오르내리는 중이면 그 구간으로 '경계 확인 중'
        if (setup == null && atrOk && EntryPlanner.isReferencePhase(phase)) {
            setup = EntryPlanner.boundaryWatch(in, zones).orElse(null);
            if (setup != null) {
                verdict = Verdict.BOUNDARY_WATCH;
                EntrySignalText.report(verdict, setup, builder);
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

    /** 장기 추세선: 주봉 40개(약 200거래일) 종가 평균. 일봉은 100개뿐이라 200일선 대신 주봉으로 계산한다 */
    private static final int LONG_TREND_WEEKS = 40;

    /** 현재가가 주봉 40주선 아래면 true. 이때는 눌림목·돌파 진입을 내지 않는다 */
    static boolean belowLongTrend(MarketContext ctx) {
        var weekly = ctx.priceSeries() != null ? ctx.priceSeries().weeklyCharts() : null;
        if (weekly == null || weekly.size() < LONG_TREND_WEEKS) return false;
        int n = weekly.size();
        double sum = 0;
        for (int i = n - LONG_TREND_WEEKS; i < n; i++) sum += weekly.get(i).close().doubleValue();
        double ma = sum / LONG_TREND_WEEKS;
        return Double.isFinite(ma) && ma > 0 && ctx.currentPrice() < ma;
    }
}
