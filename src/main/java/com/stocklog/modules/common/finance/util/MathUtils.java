package com.stocklog.modules.common.finance.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MathUtils {

    public static double sma(List<Double> prices, int endIdx, int period) {
        if (endIdx < period - 1) return 0;
        double sum = 0;
        for (int i = endIdx; i > endIdx - period; i--) sum += prices.get(i);
        return sum / period;
    }

    public static double stdDev(List<Double> data, int endIdx, int period) {
        if (endIdx < period - 1) return 0;
        double mean = sma(data, endIdx, period);
        double sumSq = 0;
        for (int i = endIdx; i > endIdx - period; i--) {
            sumSq += Math.pow(data.get(i) - mean, 2);
        }
        return Math.sqrt(sumSq / period);
    }

    public static double highest(List<Double> data, int endIdx, int period) {
        if (endIdx < period - 1) return data.get(endIdx);
        double max = -Double.MAX_VALUE;
        for (int i = endIdx; i > endIdx - period; i--) {
            if (data.get(i) > max) max = data.get(i);
        }
        return max;
    }

    public static double lowest(List<Double> data, int endIdx, int period) {
        if (endIdx < period - 1) return data.get(endIdx);
        double min = Double.MAX_VALUE;
        for (int i = endIdx; i > endIdx - period; i--) {
            if (data.get(i) < min) min = data.get(i);
        }
        return min;
    }

    public static double[] calcEmaArray(List<Double> prices, int period) {
        double[] ema = new double[prices.size()];
        if (prices.size() < period) return ema;

        double k = 2.0 / (period + 1);
        double sum = 0;
        for (int i = 0; i < period; i++) sum += prices.get(i);
        ema[period - 1] = sum / period;

        for (int i = period; i < prices.size(); i++) {
            ema[i] = prices.get(i) * k + ema[i - 1] * (1 - k);
        }
        return ema;
    }

    public static double[] calcEmaFromArray(double[] src, int period, int startIndex) {
        double[] ema = new double[src.length];
        if (src.length < startIndex + period) return ema;

        double k = 2.0 / (period + 1);
        double sum = 0;
        for (int i = startIndex; i < startIndex + period; i++) sum += src[i];

        ema[startIndex + period - 1] = sum / period;
        for (int i = startIndex + period; i < src.length; i++) {
            ema[i] = src[i] * k + ema[i - 1] * (1 - k);
        }
        return ema;
    }
}