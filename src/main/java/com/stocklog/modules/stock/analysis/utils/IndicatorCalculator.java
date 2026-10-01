package com.stocklog.modules.stock.analysis.utils;

import com.stocklog.modules.common.finance.indicator.priceaction.PivotIndicators;
import com.stocklog.modules.common.finance.model.MacdResult;
import com.stocklog.modules.common.finance.model.Pivot;
import com.stocklog.modules.common.type.Period;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// 기술적 분석 지표 계산을 전담하는 순수 수학/통계 유틸리티 클래스
public final class IndicatorCalculator {

    // 인스턴스화 방지 (유틸리티 클래스의 정석)
    private IndicatorCalculator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ── 1. 지지/저항 지표 (Pivot) ──

    public static Pivot calcPivot(double high, double low, double close) {
        return  PivotIndicators.calcPivotPoints(high, low, close, Period.DAY);

    }

    // ── 2. 추세 및 모멘텀 지표 ──

    public static double calcAtr(List<Double> highs, List<Double> lows, List<Double> closes, int period, int lastIdx) {
        if (lastIdx < period) return 0;
        double atr = 0;
        for (int i = lastIdx - period + 1; i <= lastIdx; i++) {
            atr += getTrueRange(highs.get(i), lows.get(i), closes.get(i - 1));
        }
        return atr / period;
    }

    public static double calcAdx(List<Double> highs, List<Double> lows, List<Double> closes, int period, int lastIdx) {
        if (lastIdx < period * 2) return 0; // 충분한 데이터 확보 필요

        double[] plusDM = new double[lastIdx + 1];
        double[] minusDM = new double[lastIdx + 1];
        double[] tr = new double[lastIdx + 1];

        for (int i = 1; i <= lastIdx; i++) {
            double upMove = highs.get(i) - highs.get(i - 1);
            double downMove = lows.get(i - 1) - lows.get(i);

            plusDM[i] = (upMove > downMove && upMove > 0) ? upMove : 0;
            minusDM[i] = (downMove > upMove && downMove > 0) ? downMove : 0;
            tr[i] = getTrueRange(highs.get(i), lows.get(i), closes.get(i - 1));
        }

        // Wilder's Smoothing 적용
        double smoothTR = 0, smoothPlusDM = 0, smoothMinusDM = 0;
        for (int i = 1; i <= period; i++) {
            smoothTR += tr[i];
            smoothPlusDM += plusDM[i];
            smoothMinusDM += minusDM[i];
        }

        double[] dx = new double[lastIdx + 1];
        for (int i = period + 1; i <= lastIdx; i++) {
            smoothTR = smoothTR - (smoothTR / period) + tr[i];
            smoothPlusDM = smoothPlusDM - (smoothPlusDM / period) + plusDM[i];
            smoothMinusDM = smoothMinusDM - (smoothMinusDM / period) + minusDM[i];

            double plusDI = 100 * (smoothPlusDM / smoothTR);
            double minusDI = 100 * (smoothMinusDM / smoothTR);
            dx[i] = (plusDI + minusDI == 0) ? 0 : 100 * Math.abs(plusDI - minusDI) / (plusDI + minusDI);
        }

        double adx = 0;
        for (int i = lastIdx - period + 1; i <= lastIdx; i++) {
            adx += dx[i];
        }
        return adx / period;
    }

    public static MacdResult calcMacd(List<Double> prices) {
        int size = prices.size();
        if (size < 35) return new MacdResult(0, 0, 0, false, false, false);

        double[] ema12 = calcEmaArray(prices, 12);
        double[] ema26 = calcEmaArray(prices, 26);

        double[] macdLine = new double[size];
        for (int i = 0; i < size; i++) macdLine[i] = ema12[i] - ema26[i];

        double[] signalLine = calcEmaFromArray(macdLine, 9, 26);

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

    // ── 3. 이동평균 및 통계 지표 ──

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

    // ── 4. 볼린저 밴드 관련 지표 ──

    public static double bollingerUpper(List<Double> prices, int endIdx, int period) {
        return sma(prices, endIdx, period) + 2 * stdDev(prices, endIdx, period);
    }

    public static double bollingerLower(List<Double> prices, int endIdx, int period) {
        return sma(prices, endIdx, period) - 2 * stdDev(prices, endIdx, period);
    }

    public static boolean isBollingerSqueezeOptimized(List<Double> prices, int lastIdx, int lookback) {
        if (lastIdx < 40) return false;

        double currentBandWidth = (bollingerUpper(prices, lastIdx, 20) - bollingerLower(prices, lastIdx, 20)) / sma(prices, lastIdx, 20);
        double minBandWidth = Double.MAX_VALUE;

        for (int i = 1; i <= lookback; i++) {
            double bw = (bollingerUpper(prices, lastIdx - i, 20) - bollingerLower(prices, lastIdx - i, 20)) / sma(prices, lastIdx - i, 20);
            if (bw < minBandWidth) minBandWidth = bw;
        }
        return currentBandWidth <= minBandWidth * 1.10;
    }

    // ── 5. 거래량 관련 지표 ──

    public static double calculateProjectedVolume(double currentVolume ,double yesterdayVolume, LocalDate chartDate) {
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

    /**
     * 특정 기간 동안의 최고가를 반환합니다.
     */
    public static double highest(List<Double> data, int endIdx, int period) {
        if (endIdx < period - 1) return data.get(endIdx); // 데이터가 부족하면 현재 인덱스 값 반환

        double max = -Double.MAX_VALUE;
        for (int i = endIdx; i > endIdx - period; i--) {
            if (data.get(i) > max) {
                max = data.get(i);
            }
        }
        return max;
    }

    /**
     * 특정 기간 동안의 최저가를 반환합니다.
     */
    public static double lowest(List<Double> data, int endIdx, int period) {
        if (endIdx < period - 1) return data.get(endIdx); // 데이터가 부족하면 현재 인덱스 값 반환

        double min = Double.MAX_VALUE;
        for (int i = endIdx; i > endIdx - period; i--) {
            if (data.get(i) < min) {
                min = data.get(i);
            }
        }
        return min;
    }

    // ── VWAP (거래량 가중 평균가) ──
    /**
     * 특정 기간 동안의 거래량 가중 평균가(VWAP)를 계산합니다.
     */
    public static double calcVwap(List<Double> highs, List<Double> lows, List<Double> closes, List<Double> volumes, int endIdx, int period) {
        if (endIdx < period - 1) return 0; // 데이터 부족 시

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

    // ── POC (최대 매물대 / Point of Control) ──
    /**
     * 특정 기간 동안 거래량이 가장 많이 쌓인 가격대(POC)를 반환합니다.
     * @param binCount 가격을 나눌 층의 개수 (보통 15~30 사용)
     */
    public static double calcPoc(List<Double> highs, List<Double> lows, List<Double> closes, List<Double> volumes, int endIdx, int period, int binCount) {
        if (endIdx < period - 1 || binCount <= 0) return 0;

        double maxPrice = -Double.MAX_VALUE;
        double minPrice = Double.MAX_VALUE;

        // 1. 기간 내 최고가, 최저가 탐색
        for (int i = endIdx - period + 1; i <= endIdx; i++) {
            if (highs.get(i) > maxPrice) maxPrice = highs.get(i);
            if (lows.get(i) < minPrice) minPrice = lows.get(i);
        }

        if (maxPrice == minPrice) return closes.get(endIdx); // 가격 변동이 없는 경우

        // 2. 구간(Bin) 크기 계산
        double binSize = (maxPrice - minPrice) / binCount;
        double[] volumeBins = new double[binCount];

        // 3. 거래량 분배 (Typical Price 기준)
        for (int i = endIdx - period + 1; i <= endIdx; i++) {
            double typicalPrice = (highs.get(i) + lows.get(i) + closes.get(i)) / 3.0;

            int binIndex = (int) ((typicalPrice - minPrice) / binSize);
            if (binIndex >= binCount) binIndex = binCount - 1; // 배열 초과 방지

            volumeBins[binIndex] += volumes.get(i);
        }

        // 4. 최대 거래량이 터진 구간(POC) 찾기
        int pocIndex = 0;
        double maxVol = 0;
        for (int i = 0; i < binCount; i++) {
            if (volumeBins[i] > maxVol) {
                maxVol = volumeBins[i];
                pocIndex = i;
            }
        }

        // 해당 구간의 중간 가격을 반환
        return minPrice + (pocIndex * binSize) + (binSize / 2.0);
    }





    // ── 6. 내부 계산용 헬퍼 메서드 (Private) ──

    private static double getTrueRange(double high, double low, double prevClose) {
        double hl = high - low;
        double hc = Math.abs(high - prevClose);
        double lc = Math.abs(low  - prevClose);
        return Math.max(hl, Math.max(hc, lc));
    }

    private static double[] calcEmaArray(List<Double> prices, int period) {
        double[] ema = new double[prices.size()];
        if (prices.size() < period) return ema;

        double k   = 2.0 / (period + 1);
        double sum = 0;
        for (int i = 0; i < period; i++) sum += prices.get(i);
        ema[period - 1] = sum / period;

        for (int i = period; i < prices.size(); i++) {
            ema[i] = prices.get(i) * k + ema[i - 1] * (1 - k);
        }
        return ema;
    }

    private static double[] calcEmaFromArray(double[] src, int period, int startIndex) {
        double[] ema = new double[src.length];
        if (src.length < startIndex + period) return ema;

        double k   = 2.0 / (period + 1);
        double sum = 0;
        for (int i = startIndex; i < startIndex + period; i++) sum += src[i];

        ema[startIndex + period - 1] = sum / period;
        for (int i = startIndex + period; i < src.length; i++) {
            ema[i] = src[i] * k + ema[i - 1] * (1 - k);
        }
        return ema;
    }
}