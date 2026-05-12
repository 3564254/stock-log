package com.stocklog.domain.stock.analysis.domain.signal.vo;

import com.stocklog.domain.stock.analysis.utils.IndicatorCalculator;
import com.stocklog.domain.stock.chart.dto.internal.StockChartDto;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

// 분석 시점의 모든 시장 데이터를 담고 있는 불변 객체
@Builder
public record MarketContext(
        // ── 1. 기본 차트 및 가격 데이터 ──
        List<StockChartDto> dailyCharts,
        List<StockChartDto> weeklyCharts,
        int lastIdx,
        List<Double> closes,
        List<Double> highs,
        List<Double> lows,
        List<Double> volumes,
        double currentPrice,
        double yesterdayPrice,

        // ── 2. 이동평균선 (MA) 및 이격도 ──
        double ma5,
        double ma20,
        double ma50,
        double ma5Yesterday,
        double ma20Yesterday,
        boolean isMa5Rising,
        boolean isMa20Rising,
        double distFromMa5,
        double distFromMa20,
        double distFromMa50,

        // ── 3. 볼린저 밴드 ──
        double stdDev20,
        double upper,
        double lower,
        double bandWidth,
        double percentB,
        double dynamicThreshold,
        boolean isSqueeze,

        // ── 4. 모멘텀 및 추세 지표 (RSI, ATR, ADX) ──
        double rsi,
        double atr,
        double adx,
        boolean isStrongTrend,
        boolean isWeakTrend,

        // ── 5. 거래량 분석 ──
        double currentVolume,
        double projectedVolume,
        double volumeMa5,
        boolean isVolBullish,
        boolean isVolBearish,

        // ── 6. Price Action 및 거시 추세 ──
        boolean isHH,
        boolean isHL,
        boolean isLH,
        boolean isLL,
        boolean isMacroUptrend,
        boolean isUptrend,
        boolean isSideways,



        // ── 7. 복합 지표 객체 ──
        MacdResult macd,
        Pivot dailyPivot,
        Pivot weeklyPivot
) {

    public static MarketContext getMarketContext(List<StockChartDto> dailyCharts, List<StockChartDto> weeklyCharts) {
        if (dailyCharts == null || dailyCharts.size() < 60) {
            return null; // 데이터 부족 시 방어 로직
        }

        List<Double> closes = dailyCharts.stream().map(b -> b.close().doubleValue()).collect(Collectors.toList());
        List<Double> highs = dailyCharts.stream().map(b -> b.high().doubleValue()).collect(Collectors.toList());
        List<Double> lows = dailyCharts.stream().map(b -> b.low().doubleValue()).collect(Collectors.toList());
        List<Double> volumes = dailyCharts.stream().map(b -> b.volume() != null ? b.volume().doubleValue() : 0.0).collect(Collectors.toList());

        int lastIdx = closes.size() - 1;
        double cur = closes.get(lastIdx);
        double prev = closes.get(lastIdx - 1);

        // ── 이동평균선 연산 ──
        double ma5 = IndicatorCalculator.sma(closes, lastIdx, 5);
        double ma20 = IndicatorCalculator.sma(closes, lastIdx, 20);
        double ma50 = lastIdx >= 49 ? IndicatorCalculator.sma(closes, lastIdx, 50) : Double.NaN;
        double ma5Yesterday = IndicatorCalculator.sma(closes, lastIdx - 1, 5);
        double ma20Yesterday = IndicatorCalculator.sma(closes, lastIdx - 1, 20);

        boolean isMa5Rising = ma5 > ma5Yesterday;
        boolean isMa20Rising = ma20 > ma20Yesterday;

        double distFromMa5 = (cur - ma5) / ma5 * 100;
        double distFromMa20 = (cur - ma20) / ma20 * 100;
        double distFromMa50 = !Double.isNaN(ma50) ? (cur - ma50) / ma50 * 100 : Double.NaN;

        // ── 볼린저 밴드 연산 ──
        double stdDev20 = IndicatorCalculator.stdDev(closes, lastIdx, 20);
        double upper = ma20 + 2 * stdDev20;
        double lower = ma20 - 2 * stdDev20;
        double bandWidth = ma20 > 0 ? (upper - lower) / ma20 * 100 : 0;
        double dynamicThreshold = Math.max(5.0, bandWidth * 0.5);
        double percentB = (upper - lower) > 0 ? (cur - lower) / (upper - lower) : 0.5;
        boolean isSqueeze = IndicatorCalculator.isBollingerSqueezeOptimized(closes, lastIdx, 20);

        // ── 모멘텀 연산 ──
        double rsi = IndicatorCalculator.calcRsiWilder(closes, 14, lastIdx);
        double atr = IndicatorCalculator.calcAtr(highs, lows, closes, 14, lastIdx);
        double adx = IndicatorCalculator.calcAdx(highs, lows, closes, 14, lastIdx);
        boolean isStrongTrend = adx >= 25;
        boolean isWeakTrend = adx < 20;

        // ── 거래량 연산 ──
        double currentVolume = volumes.get(lastIdx);
        LocalDate chartDate = dailyCharts.get(lastIdx).time();
        double projectedVolume = IndicatorCalculator.calculateProjectedVolume(currentVolume, chartDate);
        double volumeMa5 = IndicatorCalculator.sma(volumes, lastIdx - 1, 5);
        double volumeMa20 = IndicatorCalculator.sma(volumes, lastIdx - 1, 20);

        double volumeRatio = volumeMa20 > 0 ? projectedVolume / volumeMa20 : 0;
        boolean isStrongVolume = volumeRatio >= 1.5;
        boolean isVolBullish = (cur > prev) && isStrongVolume;
        boolean isVolBearish = (cur < prev) && isStrongVolume;

        // ── Price Action 연산 ──
        boolean isHH = highs.get(lastIdx) > highs.get(lastIdx - 1) && highs.get(lastIdx - 1) >= highs.get(lastIdx - 2);
        boolean isHL = lows.get(lastIdx) > lows.get(lastIdx - 1) && lows.get(lastIdx - 1) >= lows.get(lastIdx - 2);
        boolean isLH = highs.get(lastIdx) < highs.get(lastIdx - 1) && highs.get(lastIdx - 1) <= highs.get(lastIdx - 2);
        boolean isLL = lows.get(lastIdx) < lows.get(lastIdx - 1) && lows.get(lastIdx - 1) <= lows.get(lastIdx - 2);

        boolean isMacroUptrend = !Double.isNaN(ma50) && cur > ma50;
        boolean isUptrend = (ma5 > ma20) && (ma20 > ma50) && isMa20Rising;
        boolean isSideways = !isUptrend && adx < 20;

        // ── 복합 객체 연산 ──
        MacdResult macd = IndicatorCalculator.calcMacd(closes);

        StockChartDto yesterday = dailyCharts.get(lastIdx - 1);
        double yh = yesterday.high() != null ? yesterday.high().doubleValue() : prev;
        double yl = yesterday.low() != null ? yesterday.low().doubleValue() : prev;
        double yc = yesterday.close().doubleValue();
        Pivot dailyPivot = IndicatorCalculator.calcPivot(yh, yl, yc);

        Pivot weeklyPivot = null;
        if (weeklyCharts != null && weeklyCharts.size() >= 2) {
            StockChartDto lastWeek = weeklyCharts.get(weeklyCharts.size() - 2);
            double wyh = lastWeek.high() != null ? lastWeek.high().doubleValue() : prev;
            double wyl = lastWeek.low() != null ? lastWeek.low().doubleValue() : prev;
            double wyc = lastWeek.close().doubleValue();
            weeklyPivot = IndicatorCalculator.calcPivot(wyh, wyl, wyc);
        }

        // 완성된 데이터 조립 및 반환
        return MarketContext.builder()
                .dailyCharts(dailyCharts).weeklyCharts(weeklyCharts)
                .lastIdx(lastIdx).closes(closes).highs(highs).lows(lows).volumes(volumes)
                .currentPrice(cur).yesterdayPrice(prev)
                .ma5(ma5).ma20(ma20).ma50(ma50)
                .ma5Yesterday(ma5Yesterday).isMa5Rising(isMa5Rising)
                .ma20Yesterday(ma20Yesterday).isMa20Rising(isMa20Rising)
                .distFromMa5(distFromMa5).distFromMa20(distFromMa20).distFromMa50(distFromMa50)
                .stdDev20(stdDev20).upper(upper).lower(lower).bandWidth(bandWidth).percentB(percentB).dynamicThreshold(dynamicThreshold).isSqueeze(isSqueeze)
                .rsi(rsi).atr(atr).adx(adx).isStrongTrend(isStrongTrend).isWeakTrend(isWeakTrend)
                .currentVolume(currentVolume).projectedVolume(projectedVolume).volumeMa5(volumeMa5).isVolBullish(isVolBullish).isVolBearish(isVolBearish)
                .isHH(isHH).isHL(isHL).isLH(isLH).isLL(isLL).isMacroUptrend(isMacroUptrend).isUptrend(isUptrend).isSideways(isSideways)
                .macd(macd).dailyPivot(dailyPivot).weeklyPivot(weeklyPivot)
                .build();
    }
}