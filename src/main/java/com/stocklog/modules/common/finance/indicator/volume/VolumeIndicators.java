package com.stocklog.modules.common.finance.indicator.volume;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.model.ExchangeSession;
import com.stocklog.modules.common.finance.model.VolumeAnalysis;
import com.stocklog.modules.common.finance.series.PriceSeries;
import com.stocklog.modules.common.finance.util.MathUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

public final class VolumeIndicators {

    /** 개장 후 누적 거래량 비중이 이 값 미만이면(약 30분) 예측이 불안정해 거래량 판단을 보류한다. */
    private static final double MIN_SHARE = 0.15;

    /**
     * U자 거래량 곡선의 굴곡 정도(0이면 장중 균등, 1에 가까울수록 개장·마감 쏠림이 큼).
     * 임시 근사값이므로 분봉 거래량 이력으로 보정하는 것이 좋다.
     */
    private static final double U_SHAPE = 0.9;

    private VolumeIndicators() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * @param volume   장중이면 예상 거래량, 아니면 원본 거래량
     * @param intraday 오늘 장이 진행 중이라 volume이 예상치인지
     * @param reliable false면 장 초반이라 예상치를 신뢰할 수 없음 (거래량 플래그를 켜지 말 것)
     */
    public record ProjectedVolume(double volume, boolean intraday, boolean reliable) {}

    /**
     * 개장 후 경과 비율 t(0~1)까지의 누적 거래량 비중.
     * 밀도 u(t) = 1 + a·cos(2πt) 를 적분한 값으로, 개장 직후와 마감 직전에 거래량이 몰리는 U자를 근사한다.
     */
    static double cumulativeShare(double t) {
        return t + U_SHAPE * Math.sin(2 * Math.PI * t) / (2 * Math.PI);
    }

    /**
     * @deprecated 시장 정보가 없으면 장중 보정을 할 수 없다. 이전 구현은 시장을 구분하지 않아
     * 국내 종목에도 미국장 시간대를 적용해 거래량을 최대 13배까지 부풀렸다.
     * {@link #projectVolume(double, double, LocalDate, ExchangeSession, Clock)}를 사용할 것.
     */
    @Deprecated
    public static double calculateProjectedVolume(double currentVolume, double yesterdayVolume, LocalDate chartDate) {
        return currentVolume < 1 ? yesterdayVolume : currentVolume;
    }

    public static ProjectedVolume projectVolume(double currentVolume, double yesterdayVolume,
                                                LocalDate chartDate, ExchangeSession session, Clock clock) {
        if (currentVolume < 1) return new ProjectedVolume(yesterdayVolume, false, true);
        if (session == null) return new ProjectedVolume(currentVolume, false, true);

        ZonedDateTime now = ZonedDateTime.now(clock.withZone(session.zone()));
        ProjectedVolume complete = new ProjectedVolume(currentVolume, false, true);

        // 마지막 봉이 오늘이 아니면(휴장일·개장 전) 이미 끝난 하루치 거래량이다
        if (!chartDate.equals(now.toLocalDate())) return complete;

        LocalTime time = now.toLocalTime();
        if (time.isBefore(session.open())) return complete;

        long elapsed = Duration.between(session.open(), time).toMinutes();
        if (elapsed >= session.minutes()) return complete;   // 장 마감 이후

        double share = cumulativeShare((double) elapsed / session.minutes());
        return new ProjectedVolume(currentVolume / Math.max(share, MIN_SHARE), true, share >= MIN_SHARE);
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

    /** 시장을 모르면(null) 장중 보정 없이 원본 거래량으로 판정한다. */
    public static VolumeAnalysis calcVolumeAnalysis(PriceSeries priceSeries) {
        return calcVolumeAnalysis(priceSeries, null);
    }

    public static VolumeAnalysis calcVolumeAnalysis(PriceSeries priceSeries, ExchangeSession session) {
        return calcVolumeAnalysis(priceSeries, session, Clock.systemUTC());
    }

    /** 테스트에서는 Clock.fixed(...)를 넣어 장중/장마감 시나리오를 재현할 수 있다. */
    public static VolumeAnalysis calcVolumeAnalysis(PriceSeries priceSeries, ExchangeSession session, Clock clock) {
        List<Double> closes = priceSeries.closes();
        List<Double> highs = priceSeries.highs();
        List<Double> lows = priceSeries.lows();
        List<Double> volumes = priceSeries.volumes();

        // 데이터 검증을 먼저 한다 (이전에는 검증 전에 dailyCharts().get(lastIdx)를 읽었다)
        if (MarketContext.invalidate(closes) || highs == null || lows == null || volumes == null) {
            return VolumeAnalysis.createFallback();
        }

        int lastIdx = priceSeries.lastIdx();
        LocalDate lastIdxDate = priceSeries.dailyCharts().get(lastIdx).date();
        double currentPrice = priceSeries.currentPrice();
        double yesterdayPrice = priceSeries.yesterdayPrice();

        double currentVolume = volumes.get(lastIdx);
        double yesterdayVolume = volumes.get(lastIdx - 1);

        ProjectedVolume projected = projectVolume(currentVolume, yesterdayVolume, lastIdxDate, session, clock);
        double projectedVolume = projected.volume();
        double volumeMa5 = MathUtils.sma(volumes, lastIdx - 1, 5);
        double volumeMa20 = MathUtils.sma(volumes, lastIdx - 1, 20);

        double volumeRatio = volumeMa20 > 0 ? projectedVolume / volumeMa20 : 0;

        // 장 초반(예측 불안정)이거나 평균 거래량이 없으면 거래량 플래그를 켜지 않는다.
        // (이전에는 ma20이 0이면 ratio가 0이 되어 isVolumeDryingUp이 true가 되었다)
        boolean usable = projected.reliable() && volumeMa20 > 0;
        boolean isVolumeDryingUp = usable && volumeRatio < 0.7;
        boolean isStrongVolume = usable && volumeRatio >= 1.5;

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
                .volumeRatio(volumeRatio)
                .projectionReliable(projected.reliable())
                .isIntraday(projected.intraday())
                .build();
    }
}