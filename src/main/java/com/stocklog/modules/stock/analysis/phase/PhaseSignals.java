package com.stocklog.modules.stock.analysis.phase;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.indicator.phase.PhaseIndicators;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.stock.analysis.signal.SignalStrengthBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PhaseSignals {

    public static void addPhaseSpecificSignals(ChartPhase phase, MarketContext ctx, SignalStrengthBuilder builder) {
        evaluateUptrendSignal(phase, ctx, builder);

        double price = ctx.currentPrice();
        double dist  = ctx.movingAverages().distFromMa20();
        double rsi   = ctx.momentum().rsi();
        double s2    = ctx.pivots().dailyPivot().s2();

        switch (phase) {
            case ENTRY_READY -> classifyEntryType(phase, ctx, builder);

            case STRONG_HOLD -> builder.addBuySignal(String.format(
                    "🚀 [강세 지속] 추세 강도가 높고 상승 구조 유지 중 (20일선 이격 %+.1f%%). 5일선 이탈 시 모멘텀 둔화 점검", dist));

            case RE_ENTRY -> builder.addBuySignal(
                    "🎯 [재진입 후보] 상승 추세 속 VWAP(20) 부근에서 매수 거래량을 동반한 반등. 거래량 유지 여부 확인");

            case SETUP -> builder.addBuySignal(ctx.bollingerBand().isSqueeze()
                    ? "⚡ [에너지 응축] 볼린저 밴드 수축 중. 방향은 미정이므로 돌파·이탈 확인 후 대응"
                    : "⚡ [에너지 응축] 횡보 속 거래량 감소. 방향은 미정이므로 돌파·이탈 확인 후 대응");

            case COOLDOWN -> builder.addBuySignal(
                    "🧊 [건전한 조정] 20일선 부근에서 거래량이 줄며 눌리는 구간. 20일선 지지 여부 관찰");

            case RECOVERY_WATCH -> builder.addBuySignal(ctx.macd().isGoldenCross()
                    ? "🌱 [회복 초기] 저점 상승(HL)과 MACD 골든크로스 동시 확인. 큰 추세는 아직 비상승이라 전환 확정은 아님"
                    : "🌱 [회복 초기] 저점 상승(HL)과 MACD 히스토그램 개선. 추세 전환 확정 전 관찰 구간");

            // 하락 한복판이므로 매수 신호로 내지 않는다. addWatchSignal이 없으면 추가하거나 addSellSignal로 대체
            case CAPITULATION -> builder.addWatchSignal(
                    "📉 [투매 구간] 3차 지지선(S3) 이탈 + 매도 거래량 우세. 기술적 반등은 가능하나 바닥 확정은 아님");

            case FAKE_OUT -> builder.addSellSignal(
                    "⚠️ [돌파 실패] 저항선 돌파 후 다시 밀려 내려옴. 불트랩 가능성");

            case WARNING_ZONE -> builder.addSellSignal(
                    "⚠️ [과열 경고] " + String.join(" · ", PhaseIndicators.warningReasons(ctx)));

            case CLIMAX -> builder.addSellSignal(String.format(
                    "🔥 [과열 극단] RSI %.0f, 볼린저 %%B 과열에 소진 신호 동반. 신규 추격은 위험, 보유분은 이탈 기준 점검", rsi));

            case SCALE_OUT -> builder.addSellSignal(
                    "💰 [모멘텀 둔화] 고점 하락(LH)과 5일선 이탈, 강한 추세는 아님. 수익 구간이라면 비중 조절 검토");

            case BEAR_RALLY -> builder.addSellSignal(
                    "🐻 [하락장 반등] 큰 추세는 비상승인데 단기 상승 구조. 추세 전환 확인 전까지 반등 한계 가능성");

            case STOP_LOSS -> builder.addSellSignal(String.format(
                    "🚨 [지지 이탈] 현재가 %,.0f가 2차 지지선(S2 %,.0f) 아래. 보유 중이라면 리스크 관리 기준 점검", price, s2));

            case DOWNTREND_CONFIRMED -> builder.addSellSignal(
                    "📉 [하락 확정] 고점·저점이 모두 낮아지고(LH/LL) 매도 거래량 우세");

            case DANGER_ZONE -> builder.addSellSignal(
                    "📉 [하락 추세권] 큰 추세가 비상승이고 뚜렷한 회복 신호 없음");

            case WATCH, INSUFFICIENT_DATA -> { }
        }
    }

    private static void evaluateUptrendSignal(ChartPhase phase, MarketContext ctx, SignalStrengthBuilder builder) {
        if (ctx.priceAction().isUptrend() && phase != ChartPhase.DANGER_ZONE && phase != ChartPhase.WATCH && phase != ChartPhase.DOWNTREND_CONFIRMED) {
            builder.addBuySignal("✅ [추세 확립] 정배열 및 20일선 우상향 기울기 확보");
        }
    }

    private static void classifyEntryType(ChartPhase phase, MarketContext ctx, SignalStrengthBuilder builder) {
        switch (PhaseIndicators.classifyEntryType(ctx, phase)) {
            case BREAKOUT -> builder.addBuySignal("⚔️ [돌파 타점] 의미 있는 저항선을 강한 거래량과 함께 뚫어내는 공격적 진입 포착");
            case PULLBACK -> builder.addBuySignal("🛡️ [눌림목 타점] 상승 추세 속 주요 이평선 지지를 받으며 반등을 준비하는 구간");
            case RECLAIM  -> builder.addBuySignal("🔄 [탈환 타점] 일시 이탈했던 중요 라인을 재돌파하며 지지를 확인하는 회복 타점");
            default       -> builder.addBuySignal("🎯 [진입 대기] 주요 저항선 돌파 직전 매수세 응축 및 타점 탐색 중");
        }
    }
}
