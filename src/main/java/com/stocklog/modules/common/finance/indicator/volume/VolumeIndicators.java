package com.stocklog.modules.common.finance.indicator.volume;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.model.VolumeAnalysis;
import com.stocklog.modules.common.finance.series.PriceSeries;
import com.stocklog.modules.common.finance.util.MathUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class VolumeIndicators {

    private VolumeIndicators() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static double calculateProjectedVolume(double currentVolume, double yesterdayVolume, LocalDate chartDate) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (currentVolume < 1) {
            return yesterdayVolume;
        }

        boolean isMidnightUsMarket = now.isBefore(LocalTime.of(6, 0)) && chartDate.equals(today.minusDays(1));

        if (!chartDate.equals(today) && !isMidnightUsMarket) {
            return currentVolume;
        }

        int elapsedMinutes = 390;

        if (now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(15, 30))) {
            elapsedMinutes = (now.getHour() - 9) * 60 + now.getMinute();
        } else if (now.isAfter(LocalTime.of(22, 30))) {
            elapsedMinutes = (now.getHour() - 22) * 60 + now.getMinute() - 30;
        } else if (now.isBefore(LocalTime.of(6, 0))) {
            elapsedMinutes = (now.getHour() + 1) * 60 + now.getMinute() + 30;
        } else {
            return currentVolume;
        }

        elapsedMinutes = Math.max(elapsedMinutes, 30);
        return currentVolume * (390.0 / elapsedMinutes);
    }

    public static double calcVwap(List<Double> highs, List<Double> lows, List<Double> closes, List<Double> volumes, int endIdx, int period) {
        if (endIdx < period - 1) return 0;

        double cumulativeVolumePrice = 0;
        double cumulativeVolume = 0;

        for (int i = endIdx - period + 1; i <= endIdx; i++) {
            double typicalPrice = (highs.get(i) + lows.get(i) + closes.get(i)) / 3.0;
            double vol = volumes.get(i);

            cumulativeVolumePrice += typicalPrice * vol;
            cumulativeVolume += vol;
        }

        return cumulativeVolume == 0 ? 0 : cumulativeVolumePrice / cumulativeVolume;
    }

    public static double calcPoc(List<Double> highs, List<Double> lows, List<Double> closes, List<Double> volumes, int endIdx, int period, int binCount) {
        if (endIdx < period - 1 || binCount <= 0) return 0;

        double maxPrice = -Double.MAX_VALUE;
        double minPrice = Double.MAX_VALUE;

        for (int i = endIdx - period + 1; i <= endIdx; i++) {
            if (highs.get(i) > maxPrice) maxPrice = highs.get(i);
            if (lows.get(i) < minPrice) minPrice = lows.get(i);
        }

        if (maxPrice == minPrice) return closes.get(endIdx);

        double binSize = (maxPrice - minPrice) / binCount;
        double[] volumeBins = new double[binCount];

        for (int i = endIdx - period + 1; i <= endIdx; i++) {
            double typicalPrice = (highs.get(i) + lows.get(i) + closes.get(i)) / 3.0;
            int binIndex = (int) ((typicalPrice - minPrice) / binSize);
            if (binIndex >= binCount) binIndex = binCount - 1;

            volumeBins[binIndex] += volumes.get(i);
        }

        int pocIndex = 0;
        double maxVol = 0;
        for (int i = 0; i < binCount; i++) {
            if (volumeBins[i] > maxVol) {
                maxVol = volumeBins[i];
                pocIndex = i;
            }
        }

        return minPrice + (pocIndex * binSize) + (binSize / 2.0);
    }

    public static VolumeAnalysis calcVolumeAnalysis(PriceSeries priceSeries) {
        List<Double> closes = priceSeries.closes();
        List<Double> highs = priceSeries.highs();
        List<Double> lows = priceSeries.lows();
        List<Double> volumes = priceSeries.volumes();
        int lastIdx = priceSeries.lastIdx();
        LocalDate lastIdxDate = priceSeries.dailyCharts().get(lastIdx).date();
        double currentPrice = priceSeries.currentPrice();
        double yesterdayPrice = priceSeries.yesterdayPrice();

        if (MarketContext.invalidate(closes)) return VolumeAnalysis.createFallback();

        double currentVolume = volumes.get(lastIdx);
        double yesterdayVolume = volumes.get(lastIdx - 1);

        // 기존 VolumeIndicators 내부의 메서드 활용
        double projectedVolume = calculateProjectedVolume(currentVolume, yesterdayVolume, lastIdxDate);
        double volumeMa5 = MathUtils.sma(volumes, lastIdx - 1, 5);
        double volumeMa20 = MathUtils.sma(volumes, lastIdx - 1, 20);

        double volumeRatio = volumeMa20 > 0 ? projectedVolume / volumeMa20 : 0;
        boolean isVolumeDryingUp = volumeRatio < 0.7;
        boolean isStrongVolume = volumeRatio >= 1.5;

        double priceChangeRatio = yesterdayPrice > 0 ? (currentPrice - yesterdayPrice) / yesterdayPrice : 0;
        boolean isMeaningfulUp = priceChangeRatio > 0.005;
        boolean isMeaningfulDown = priceChangeRatio < -0.005;

        boolean isVolBullish = isStrongVolume && isMeaningfulUp;
        boolean isVolBearish = isStrongVolume && isMeaningfulDown;

        double vwap20 = calcVwap(highs, lows, closes, volumes, lastIdx, 20);
        double pocPrice = calcPoc(highs, lows, closes, volumes, lastIdx, Math.min(60, closes.size()), 15);

        return VolumeAnalysis.builder()
                .currentVolume(currentVolume).projectedVolume(projectedVolume)
                .volumeMa5(volumeMa5).volumeMa20(volumeMa20)
                .isVolBullish(isVolBullish).isVolBearish(isVolBearish).isVolumeDryingUp(isVolumeDryingUp)
                .vwap20(vwap20).pocPrice(pocPrice)
                .build();
    }
}