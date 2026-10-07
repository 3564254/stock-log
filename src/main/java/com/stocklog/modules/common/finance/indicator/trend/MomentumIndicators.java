package com.stocklog.modules.common.finance.indicator.trend;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.model.MomentumMetrics;
import com.stocklog.modules.common.finance.series.PriceSeries;
import com.stocklog.modules.common.finance.util.MathUtils;
import com.stocklog.modules.common.finance.model.MacdResult;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MomentumIndicators {

    /**
     * 변동성 계산을 위한 True Range (도메인 지표)
     */
    public static double trueRange(double high, double low, double prevClose) {
        double hl = high - low;
        double hc = Math.abs(high - prevClose);
        double lc = Math.abs(low - prevClose);
        return Math.max(hl, Math.max(hc, lc));
    }

    public static double calcAtr(List<Double> highs, List<Double> lows, List<Double> closes, int period, int lastIdx) {
        if (lastIdx < period) return 0;

        // 1) 첫 ATR = 처음 period개 TR의 단순평균 (TR은 i=1부터 계산 가능)
        double atr = 0;
        for (int i = 1; i <= period; i++) {
            atr += trueRange(highs.get(i), lows.get(i), closes.get(i - 1));
        }
        atr /= period;

        // 2) 이후 Wilder 평활: ATR = (prevATR * (period-1) + TR) / period
        for (int i = period + 1; i <= lastIdx; i++) {
            double tr = trueRange(highs.get(i), lows.get(i), closes.get(i - 1));
            atr = (atr * (period - 1) + tr) / period;
        }
        return atr;
    }

    public static double calcRsiWilder(List<Double> prices, int period, int lastIdx) {
        if (lastIdx <= period) return 50;

        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = prices.get(i) - prices.get(i - 1);
            if (diff > 0) avgGain += diff;
            else          avgLoss -= diff;
        }
        avgGain /= period;
        avgLoss /= period;

        for (int i = period + 1; i <= lastIdx; i++) {
            double diff = prices.get(i) - prices.get(i - 1);
            double gain = diff > 0 ? diff : 0;
            double loss = diff < 0 ? -diff : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0) return 100;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    public static MacdResult calcMacd(List<Double> prices) {
        int size = prices.size();
        if (size < 35) return new MacdResult(0, 0, 0, false, false, false);

        double[] ema12 = MathUtils.calcEmaArray(prices, 12);
        double[] ema26 = MathUtils.calcEmaArray(prices, 26);

        double[] macdLine = new double[size];
        for (int i = 0; i < size; i++) macdLine[i] = ema12[i] - ema26[i];

        double[] signalLine = MathUtils.calcEmaFromArray(macdLine, 9, 26);

        double macd = macdLine[size - 1];
        double signal = signalLine[size - 1];
        double histogram = macd - signal;

        double prevMacd   = macdLine[size - 2];
        double prevSignal = signalLine[size - 2];
        double prevHist   = macdLine[size - 2] - signalLine[size - 2];

        boolean isGoldenCross = (prevMacd <= prevSignal) && (macd > signal);
        boolean isDeadCross   = (prevMacd >= prevSignal) && (macd < signal);
        boolean isHistogramRising = histogram > prevHist;

        return new MacdResult(macd, signal, histogram, isGoldenCross, isDeadCross, isHistogramRising);
    }

    public static double calcAdx(List<Double> highs, List<Double> lows, List<Double> closes, int period, int lastIdx) {
        if (lastIdx < period * 2) return 0;

        double[] plusDM = new double[lastIdx + 1];
        double[] minusDM = new double[lastIdx + 1];
        double[] tr = new double[lastIdx + 1];

        for (int i = 1; i <= lastIdx; i++) {
            double upMove = highs.get(i) - highs.get(i - 1);
            double downMove = lows.get(i - 1) - lows.get(i);

            plusDM[i] = (upMove > downMove && upMove > 0) ? upMove : 0;
            minusDM[i] = (downMove > upMove && downMove > 0) ? downMove : 0;
            tr[i] = trueRange(highs.get(i), lows.get(i), closes.get(i - 1)); // 내부 trueRange 호출
        }

        double smoothTR = 0, smoothPlusDM = 0, smoothMinusDM = 0;
        for (int i = 1; i <= period; i++) {
            smoothTR += tr[i];
            smoothPlusDM += plusDM[i];
            smoothMinusDM += minusDM[i];
        }

        double[] dx = new double[lastIdx + 1];
        for (int i = period; i <= lastIdx; i++) {
            if (i > period) {
                smoothTR      = smoothTR      - (smoothTR / period)      + tr[i];
                smoothPlusDM  = smoothPlusDM  - (smoothPlusDM / period)  + plusDM[i];
                smoothMinusDM = smoothMinusDM - (smoothMinusDM / period) + minusDM[i];
            }
            if (smoothTR == 0) { dx[i] = 0; continue; }

            double plusDI  = 100 * smoothPlusDM / smoothTR;
            double minusDI = 100 * smoothMinusDM / smoothTR;
            double sum = plusDI + minusDI;
            dx[i] = (sum == 0) ? 0 : 100 * Math.abs(plusDI - minusDI) / sum;
        }

// 첫 ADX = 처음 period개 DX의 평균, 이후 Wilder 평활
        double adx = 0;
        for (int i = period; i < period * 2; i++) adx += dx[i];
        adx /= period;
        for (int i = period * 2; i <= lastIdx; i++) {
            adx = (adx * (period - 1) + dx[i]) / period;
        }
        return adx;
    }

    /**
     * 수익률(Return) 계산 - 모멘텀 지표로 활용
     */
    public static double computeReturn(List<Double> prices, int endIdx, int lookback) {
        int startIdx = endIdx - lookback;
        if (startIdx < 0) return 0.0;

        double startPrice = prices.get(startIdx);
        double endPrice = prices.get(endIdx);

        if (startPrice == 0.0) return 0.0;
        return ((endPrice - startPrice) / startPrice) * 100.0;
    }

    /**
     * Rate of Change (ROC) 모멘텀 오실레이터
     */
    public static double rateOfChange(List<Double> prices, int periods) {
        if (prices.size() <= periods) return 0.0;
        double current = prices.get(prices.size() - 1);
        double previous = prices.get(prices.size() - 1 - periods);

        if (previous == 0.0) return 0.0;
        return (current - previous) / previous * 100.0;
    }

    public static MomentumMetrics calcMomentumMetrics(PriceSeries priceSeries) {
        List<Double> closes = priceSeries.closes();
        List<Double> highs = priceSeries.highs();
        List<Double> lows = priceSeries.lows();
        int lastIdx = priceSeries.lastIdx();

        if (MarketContext.invalidate(closes)) return MomentumMetrics.createFallback();

        double rsi = calcRsiWilder(closes, 14, lastIdx);

        // 고가/저가 데이터가 없는 Simple 버전 처리(예: 틱 차트 등)
        if (highs == null || lows == null || highs.isEmpty() || lows.isEmpty()) {
            return MomentumMetrics.builder()
                    .rsi(rsi).atr(0.0).adx(0.0)
                    .isStrongTrend(false).isWeakTrend(false)
                    .build();
        }

        double atr = calcAtr(highs, lows, closes, 14, lastIdx);
        double adx = calcAdx(highs, lows, closes, 14, lastIdx);
        boolean isStrongTrend = adx >= 25;
        boolean isWeakTrend = adx < 20;

        return MomentumMetrics.builder()
                .rsi(rsi).atr(atr).adx(adx)
                .isStrongTrend(isStrongTrend).isWeakTrend(isWeakTrend)
                .build();
    }
}