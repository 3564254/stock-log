package com.stocklog.modules.stock.analysis.indicator;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.stock.analysis.signal.SignalStrengthBuilder;
import com.stocklog.modules.stock.analysis.signal.SignalWeight;

import java.util.List;

import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.calcRsiWilder;
import static com.stocklog.modules.stock.analysis.signal.SignalWeight.W_POC;
import static com.stocklog.modules.stock.analysis.signal.SignalWeight.W_VWAP;

/** 지표 이벤트 신호 (MACD/볼린저, RSI/다이버전스, 이평·피봇·POC·VWAP). 상태 없는 static 유틸이다. */
public final class IndicatorSignals {

    private IndicatorSignals() {}

    // ------------------------------------------------------------------
    // 지표 이벤트 신호 (구 SignalRule 3종을 이관: Momentum / RSI / TrendAndMa)
    // 판정(Verdict)에는 관여하지 않고, 근거 문구와 buy/sell 강도만 builder에 쌓는다.
    // 이관 시 신호 조건과 가중치는 원본 그대로이며, 방어 코드만 추가했다:
    //  - volume null일 때 NPE 나던 VWAP 구간
    //  - POC 값이 비정상(NaN/0)일 때 0 나눗셈
    //  - 다이버전스: 봉이 15개 미만이라 RSI(14)를 계산할 수 없을 때
    // ------------------------------------------------------------------
    private static final int W_RSI_EXTREME = SignalWeight.RSI_EXTREME.getWeight();

    /** 지표 이벤트 신호 전체를 builder에 쌓는다. */
    public static void apply(MarketContext ctx, SignalStrengthBuilder b) {
        momentumSignals(ctx, b);
        rsiSignals(ctx, b);
        trendAndMaSignals(ctx, b);
    }

    /** MACD 크로스 + 볼린저 밴드 이탈/돌파 */
    private static void momentumSignals(MarketContext ctx, SignalStrengthBuilder b) {
        int macdW = SignalWeight.MACD_CROSS.getWeight()
                + ((ctx.momentum() != null && ctx.momentum().isStrongTrend()) ? 10 : 0);
        int bbW = SignalWeight.BOLLINGER_BREAK.getWeight();
        int volW = SignalWeight.VOL_CONFIRM.getWeight();

        if (ctx.macd().isGoldenCross()) {
            b.addBuySignal(String.format("MACD 골든크로스 (MACD: %.2f / Signal: %.2f)", ctx.macd().macd(), ctx.macd().signal()));
            b.addBuyWeight(macdW);
        } else if (ctx.macd().isDeadCross()) {
            b.addSellSignal(String.format("MACD 데드크로스 (MACD: %.2f / Signal: %.2f)", ctx.macd().macd(), ctx.macd().signal()));
            b.addSellWeight(SignalWeight.MACD_CROSS.getWeight());
        }

        boolean volBearish = ctx.volume() != null && ctx.volume().isVolBearish();
        if (ctx.currentPrice() < ctx.bollingerBand().lower()) {
            if (volBearish) {
                b.addSellSignal(String.format("🚨 볼린저 하단 강한 이탈 (투매 주의) %%B: %.2f", ctx.bollingerBand().percentB()));
                b.addSellWeight(bbW * 2);
            } else {
                b.addBuySignal(String.format("볼린저 하단 이탈 (단기 낙폭과대) %%B: %.2f", ctx.bollingerBand().percentB()));
                b.addBuyWeight(bbW);
            }
        } else if (ctx.currentPrice() > ctx.bollingerBand().upper()) {
            // 상단 돌파의 "거래량 동반"은 상승 거래량(isVolBullish)이어야 한다.
            // (원본은 isVolBearish로 되어 있어 돌파가 매도 쪽으로 해석되던 것을 수정)
            boolean volBullish = ctx.volume() != null && ctx.volume().isVolBullish();
            if (volBullish) {
                if (ctx.bollingerBand().isSqueeze()) {
                    b.addBuySignal(String.format("🚀 [스퀴즈 폭발] 볼린저 밴드 응축 후 상단 돌파 (상단 %.0f)", ctx.bollingerBand().upper()));
                    b.addBuyWeight(bbW * 2 + volW);
                } else {
                    b.addBuySignal(String.format("볼린저 상단 돌파 (거래량 동반) — 강한 추세 (상단 %.0f)", ctx.bollingerBand().upper()));
                    b.addBuyWeight(bbW + volW);
                }
            } else {
                b.addSellSignal(String.format("볼린저 상단 이탈 (단기 과열) (현재 %.0f / 상단 %.0f)", ctx.currentPrice(), ctx.bollingerBand().upper()));
                b.addSellWeight(bbW);
            }
        }
    }

    /** RSI 과매수/과매도 + 다이버전스 */
    private static void rsiSignals(MarketContext ctx, SignalStrengthBuilder b) {
        double rsi = ctx.momentum() != null ? ctx.momentum().rsi() : 0;
        int rangeModifier = ctx.momentum() != null && ctx.momentum().isWeakTrend() ? 10 : 0; // 횡보장은 RSI 비중↑

        if (rsi < 25) {
            b.addBuySignal(String.format("RSI %.1f — 극단적 과매도 (강한 반등 신호)", rsi));
            b.addBuyWeight(W_RSI_EXTREME + rangeModifier);
        } else if (rsi < 35) {
            b.addBuySignal(String.format("RSI %.1f — 과매도 구간", rsi));
            b.addBuyWeight(W_RSI_EXTREME / 2);
        } else if (rsi > 75) {
            if (ctx.movingAverages().isMa5Rising()
                    && ctx.movingAverages().distFromMa5() < ctx.bollingerBand().dynamicThreshold()) {
                b.addSellSignal(String.format("RSI %.1f (과매수) — 추세 이탈 없음 (익절 보류)", rsi));
                b.addSellWeight(W_RSI_EXTREME / 2);
            } else {
                b.addSellSignal(String.format("RSI %.1f — 극단적 과매수 및 이격 과열", rsi));
                b.addSellWeight(W_RSI_EXTREME);
            }
        } else if (rsi > 65) {
            b.addSellSignal(String.format("RSI %.1f — 과매수 구간", rsi));
            b.addSellWeight(W_RSI_EXTREME / 2);
        }

        rsiDivergence(ctx, b, rsi);
    }

    private static void rsiDivergence(MarketContext ctx, SignalStrengthBuilder b, double currentRsi) {
        final int lookback = 20;
        List<Double> closes = ctx.priceSeries().closes();
        int lastIdx = ctx.priceSeries().lastIdx();
        double price = ctx.currentPrice();
        if (closes == null || lastIdx < 15 || lastIdx >= closes.size()) return; // RSI(14) 계산 불가 구간 방어

        // 하락 다이버전스: 주가는 고점 경신, RSI는 직전 고점 대비 낮고 직전 RSI가 과매수권
        int peakIdx = lastIdx - 1;
        double peakClose = closes.get(lastIdx - 1);
        for (int i = lastIdx - 1; i >= Math.max(14, lastIdx - lookback); i--) {
            if (closes.get(i) > peakClose) { peakClose = closes.get(i); peakIdx = i; }
        }
        double peakRsi = calcRsiWilder(closes, 14, peakIdx);
        if (price >= peakClose && currentRsi < peakRsi && peakRsi > 65) {
            b.addSellSignal(String.format("🚨 [하락 다이버전스] 주가는 고점 경신(%.0f), RSI는 하락(%.1f → %.1f) — 강력한 매도 경고",
                    price, peakRsi, currentRsi));
            b.addSellWeight((int) (W_RSI_EXTREME * 1.5));
        }

        // 상승 다이버전스: 주가는 저점 경신, RSI는 직전 저점 대비 높고 직전 RSI가 과매도권
        int troughIdx = lastIdx - 1;
        double troughClose = closes.get(lastIdx - 1);
        for (int i = lastIdx - 1; i >= Math.max(14, lastIdx - lookback); i--) {
            if (closes.get(i) < troughClose) { troughClose = closes.get(i); troughIdx = i; }
        }
        double troughRsi = calcRsiWilder(closes, 14, troughIdx);
        if (price <= troughClose && currentRsi > troughRsi && troughRsi < 35) {
            b.addBuySignal(String.format("💎 [상승 다이버전스] 주가는 저점 경신(%.0f), RSI는 상승(%.1f → %.1f) — 바닥 탈출 징후",
                    price, troughRsi, currentRsi));
            b.addBuyWeight((int) (W_RSI_EXTREME * 1.5));
        }
    }

    /** 5일선 추세/이격 + 피봇 + 매물대(POC) + VWAP */
    private static void trendAndMaSignals(MarketContext ctx, SignalStrengthBuilder b) {
        int weight = SignalWeight.MA_CROSS.getWeight();
        var ma = ctx.movingAverages();
        double price = ctx.currentPrice();

        if (price > ma.ma5()) {
            if (ma.isMa5Rising()) {
                if (ma.distFromMa5() > ctx.bollingerBand().dynamicThreshold()) {
                    b.addSellSignal(String.format("[이격과열] 5일선 대비 +%.1f%% — 조정 주의", ma.distFromMa5()));
                    b.addSellWeight(weight);
                } else {
                    b.addBuySignal(String.format("[5일선 상방] 안착 및 탄력 유지 (기준: %.0f)", ma.ma5()));
                    b.addBuyWeight(weight);
                }
            } else {
                b.addBuySignal(String.format("5일선 돌파(기울기 하락) — 안착 확인 필요 (MA5: %.0f)", ma.ma5()));
                b.addBuyWeight(weight / 2);
            }
        } else if (!ma.isMa5Rising()) {
            if (Math.abs(ma.distFromMa5()) > ctx.bollingerBand().dynamicThreshold()) {
                b.addBuySignal(String.format("[과대낙폭] 5일선 이격 %.1f%% — 반등 기대", ma.distFromMa5()));
                b.addBuyWeight(weight);
            } else {
                b.addSellSignal(String.format("[5일선 하방] 단기 추세 꺾임 (기준: %.0f)", ma.ma5()));
                b.addSellWeight(weight);
            }
        }

        // 피봇 지지/저항
        int pivotW = SignalWeight.PIVOT_LEVEL.getWeight();
        double yesterday = ctx.priceSeries().yesterdayPrice();
        var pv = ctx.pivots().dailyPivot();
        double pp = pv.p();
        if (price > pp && yesterday <= pp) {
            b.addBuySignal(String.format("피벗 기준선 상향 돌파 — 단기 추세 전환 (PP: %.0f)", pp));
            b.addBuyWeight(pivotW);
        } else if (price < pp && yesterday >= pp) {
            b.addSellSignal(String.format("피벗 기준선 하향 이탈 — 단기 하방 압력 (PP: %.0f)", pp));
            b.addSellWeight(pivotW);
        }
        if (price <= pv.s2() * 1.01) {
            b.addBuySignal(String.format("피벗 2차 지지선 도달 — 강한 반등 기대 (S2: %.0f)", pv.s2()));
            b.addBuyWeight(pivotW);
        }
        if (price >= pv.r2() * 0.99) {
            b.addSellSignal(String.format("피벗 2차 저항선 도달 — 과열 주의 (R2: %.0f)", pv.r2()));
            b.addSellWeight(pivotW);
        }

        if (ctx.volume() == null) return; // 이하 거래량 기반 신호

        // 매물대(POC): 2% 이내 근접 시 지지/저항 테스트
        double poc = ctx.volume().pocPrice();
        if (Double.isFinite(poc) && poc > 0 && Math.abs((price - poc) / poc * 100) <= 2.0) {
            if (price >= poc) {
                b.addBuySignal(String.format("🎯 [매물대 지지] 최대 매물대(POC: %.0f) 안착 테스트 중", poc));
                b.addBuyWeight(W_POC.getWeight());
            } else {
                b.addSellSignal(String.format("🧱 [매물대 저항] 최대 매물대(POC: %.0f) 하단 진입 — 돌파 확인 필요", poc));
                b.addSellWeight(W_POC.getWeight());
            }
        }

        // VWAP 돌파/이탈
        double vwap = ctx.volume().vwap20();
        if (price > vwap && yesterday <= vwap) {
            b.addBuySignal(String.format("🚀 [세력선 돌파] 기관 평균단가(VWAP: %.0f) 상향 돌파", vwap));
            b.addBuyWeight(W_VWAP.getWeight());
        } else if (price < vwap && yesterday >= vwap) {
            b.addSellSignal(String.format("📉 [세력선 이탈] 기관 평균단가(VWAP: %.0f) 하향 이탈", vwap));
            b.addSellWeight(W_VWAP.getWeight());
        }
    }
}
