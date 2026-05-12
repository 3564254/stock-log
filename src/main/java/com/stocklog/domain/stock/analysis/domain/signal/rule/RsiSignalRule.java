package com.stocklog.domain.stock.analysis.domain.signal.rule;

import com.stocklog.domain.stock.analysis.domain.signal.SignalResultBuilder;
import com.stocklog.domain.stock.analysis.domain.signal.vo.MarketContext;
import com.stocklog.domain.stock.analysis.utils.IndicatorCalculator;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RsiSignalRule implements SignalRule{
    // 가중치 상수 (필요시 별도의 Enum이나 Config 클래스로 분리 가능)
    private static final int W_RSI_EXTREME = 25;

    @Override
    public void evaluate(MarketContext ctx, SignalResultBuilder builder) {
        double rsi = ctx.rsi();

        // 횡보장(약한 추세)일 경우 박스권 지표인 RSI의 가중치를 높임
        int rangeModifier = ctx.isWeakTrend() ? 10 : 0;

        // ── 1. 일반적인 과매수/과매도 판별 ──
        evaluateBasicRsi(ctx, builder, rsi, rangeModifier);

        // ── 2. 다이버전스(추세 반전) 판별 ──
        evaluateDivergence(ctx, builder, rsi);
    }

    /**
     * 1. 기본 RSI 과매수/과매도 구간 판별
     */
    private void evaluateBasicRsi(MarketContext ctx, SignalResultBuilder builder, double rsi, int rangeModifier) {
        if (rsi < 25) {
            builder.addBuySignal(String.format("RSI %.1f — 극단적 과매도 (강한 반등 신호)", rsi));
            builder.addBuyWeight(W_RSI_EXTREME + rangeModifier);
        } else if (rsi < 35) {
            builder.addBuySignal(String.format("RSI %.1f — 과매도 구간", rsi));
            builder.addBuyWeight(W_RSI_EXTREME / 2);
        } else if (rsi > 75) {
            // 추세가 살아있고 이격이 크지 않으면 익절 보류
            if (ctx.isMa5Rising() && ctx.distFromMa5() < ctx.dynamicThreshold()) {
                builder.addSellSignal(String.format("RSI %.1f (과매수) — 추세 이탈 없음 (익절 보류)", rsi));
                builder.addSellWeight(W_RSI_EXTREME / 2);
            } else {
                builder.addSellSignal(String.format("RSI %.1f — 극단적 과매수 및 이격 과열", rsi));
                builder.addSellWeight(W_RSI_EXTREME);
            }
        } else if (rsi > 65) {
            builder.addSellSignal(String.format("RSI %.1f — 과매수 구간", rsi));
            builder.addSellWeight(W_RSI_EXTREME / 2);
        }
    }

    /**
     * 2. 하락/상승 다이버전스 판별 (고점/저점 갱신 vs RSI 모멘텀 둔화)
     */
    private void evaluateDivergence(MarketContext ctx, SignalResultBuilder builder, double currentRsi) {
        int lookback = 20; // 과거 탐색 기간
        List<Double> closes = ctx.closes();
        int lastIdx = ctx.lastIdx();
        double currentPrice = ctx.currentPrice();

        // ── [A] 하락 다이버전스 (Bearish Divergence) ──
        int prevPeakIdx = lastIdx - 1;
        double prevMaxClose = closes.get(lastIdx - 1);

        // 1. 과거 20일 이내 최고점 찾기
        for (int i = lastIdx - 1; i >= Math.max(14, lastIdx - lookback); i--) {
            if (closes.get(i) > prevMaxClose) {
                prevMaxClose = closes.get(i);
                prevPeakIdx = i;
            }
        }

        // 2. 최고점 당시의 RSI 계산 (앞서 만든 유틸리티 활용)
        double prevPeakRsi = IndicatorCalculator.calcRsiWilder(closes, 14, prevPeakIdx);

        // 3. 주가는 고점 경신 or 동일한데, RSI는 고점 낮아짐 (모멘텀 둔화) + 이전 RSI가 과매수권
        if (currentPrice >= prevMaxClose && currentRsi < prevPeakRsi && prevPeakRsi > 65) {
            builder.addSellSignal(String.format("🚨 [하락 다이버전스] 주가는 고점 경신(%.0f), RSI는 하락(%.1f → %.1f) — 강력한 매도 경고", currentPrice, prevPeakRsi, currentRsi));
            builder.addSellWeight((int) (W_RSI_EXTREME * 1.5)); // 일반 과매수보다 치명적이므로 1.5배 부여
        }

        // ── [B] 상승 다이버전스 (Bullish Divergence) ──
        double prevMinClose = closes.get(lastIdx - 1);
        int prevTroughIdx = lastIdx - 1;

        for (int i = lastIdx - 1; i >= Math.max(14, lastIdx - lookback); i--) {
            if (closes.get(i) < prevMinClose) {
                prevMinClose = closes.get(i);
                prevTroughIdx = i;
            }
        }

        double prevTroughRsi = IndicatorCalculator.calcRsiWilder(closes, 14, prevTroughIdx);

        if (currentPrice <= prevMinClose && currentRsi > prevTroughRsi && prevTroughRsi < 35) {
            builder.addBuySignal(String.format("💎 [상승 다이버전스] 주가는 저점 경신(%.0f), RSI는 상승(%.1f → %.1f) — 바닥 탈출 징후", currentPrice, prevTroughRsi, currentRsi));
            builder.addBuyWeight((int) (W_RSI_EXTREME * 1.5));
        }
    }
}
