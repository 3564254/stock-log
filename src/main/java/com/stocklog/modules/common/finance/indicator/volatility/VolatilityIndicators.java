package com.stocklog.modules.common.finance.indicator.volatility;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.model.BollingerBand;
import com.stocklog.modules.common.finance.model.MovingAverages;
import com.stocklog.modules.common.finance.series.PriceSeries;
import com.stocklog.modules.common.finance.util.MathUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VolatilityIndicators {

    public static BollingerBand calcBollingerBand(PriceSeries priceSeries, MovingAverages movingAverages) {
        List<Double> closes = priceSeries.closes();
        int lastIdx = priceSeries.lastIdx();
        double ma20 = movingAverages.ma20();

        if (MarketContext.invalidate(closes)) return BollingerBand.createFallback();

        double curPrice = closes.get(lastIdx);
        int period = 20;

        double stdDev20 = MathUtils.stdDev(closes, lastIdx, period);
        double upper = ma20 + 2 * stdDev20;
        double lower = ma20 - 2 * stdDev20;
        double bandWidth = ma20 > 0 ? (upper - lower) / ma20 * 100 : 0;
        double dynamicThreshold = Math.max(5.0, bandWidth * 0.5);
        double percentB = (upper - lower) > 0 ? (curPrice - lower) / (upper - lower) : 0.5;

        // 기존에 이 클래스에 있던 isBollingerSqueezeOptimized 메서드를 호출한다고 가정
        boolean isSqueeze = isBollingerSqueezeOptimized(closes, lastIdx, 20);

        return BollingerBand.builder()
                .stdDev20(stdDev20)
                .upper(upper).lower(lower)
                .bandWidth(bandWidth).percentB(percentB)
                .dynamicThreshold(dynamicThreshold)
                .isSqueeze(isSqueeze)
                .build();
    }

    public static double bollingerUpper(List<Double> prices, int endIdx, int period) {
        return MathUtils.sma(prices, endIdx, period) + 2 * MathUtils.stdDev(prices, endIdx, period);
    }

    public static double bollingerLower(List<Double> prices, int endIdx, int period) {
        return MathUtils.sma(prices, endIdx, period) - 2 * MathUtils.stdDev(prices, endIdx, period);
    }

    public static double bollingerBandWidth(List<Double> prices, int endIdx, int period) {
        double sma = MathUtils.sma(prices, endIdx, period);
        if (sma == 0) return 0;
        return (bollingerUpper(prices, endIdx, period) - bollingerLower(prices, endIdx, period)) / sma;
    }

    public static boolean isBollingerSqueezeOptimized(List<Double> prices, int lastIdx, int lookback) {
        if (lastIdx < 40) return false;

        double currentBandWidth = bollingerBandWidth(prices, lastIdx, 20);
        double minBandWidth = Double.MAX_VALUE;

        for (int i = 1; i <= lookback; i++) {
            double bw = bollingerBandWidth(prices, lastIdx - i, 20);
            if (bw < minBandWidth) minBandWidth = bw;
        }
        return currentBandWidth <= minBandWidth * 1.10;
    }
}