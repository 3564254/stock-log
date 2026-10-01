package com.stocklog.modules.common.finance.context;

import com.stocklog.modules.common.finance.dto.CandleChartDto;
import com.stocklog.modules.common.finance.model.*;
import com.stocklog.modules.common.finance.series.PriceSeries;
import com.stocklog.modules.common.type.ChartDataType;
import com.stocklog.modules.common.finance.indicator.trend.TrendIndicators;
import com.stocklog.modules.common.finance.indicator.volatility.VolatilityIndicators;
import com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators;
import com.stocklog.modules.common.finance.indicator.volume.VolumeIndicators;
import com.stocklog.modules.common.finance.indicator.priceaction.PriceActionIndicators;
import com.stocklog.modules.common.finance.indicator.priceaction.PivotIndicators;

import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Builder
@Slf4j
public record MarketContext(
        PriceSeries priceSeries,
        MovingAverages movingAverages,
        BollingerBand bollingerBand,
        @Nullable MomentumMetrics momentum,
        @Nullable VolumeAnalysis volume,
        PriceAction priceAction,
        MacdResult macd,
        PivotSet pivots,
        ChartDataType chartDataType
) {
    public double currentPrice() {
        return priceSeries.currentPrice();
    }

    public static final int MIN_REQUIRED_BARS = 60;

    public static boolean invalidate(List<?> charts) {
        return charts == null || charts.size() < MIN_REQUIRED_BARS;
    }

    public static MarketContext createFallback() {
        return MarketContext.builder()
                .priceSeries(PriceSeries.createFallback())
                .movingAverages(MovingAverages.createFallback())
                .bollingerBand(BollingerBand.createFallback())
                .momentum(MomentumMetrics.createFallback())
                .volume(VolumeAnalysis.createFallback())
                .priceAction(PriceAction.createFallback())
                .macd(MacdResult.createFallback())
                .pivots(PivotSet.createFallback())
                .chartDataType(ChartDataType.UNKNOWN)
                .build();
    }

    public static MarketContext ofCandle(
            List<? extends CandleChartDto> dailyCharts,
            List<? extends CandleChartDto> weeklyCharts,
            ChartDataType chartDataType
    ) {
        PriceSeries priceSeries = PriceSeries.ofCandle(dailyCharts, weeklyCharts);
        if (priceSeries == null) {
            log.info("[MARKETCONTEXT] PricesSeries == null");
            return MarketContext.createFallback();
        }

        MovingAverages movingAverages = TrendIndicators.calcMovingAverages(priceSeries);
        BollingerBand bollingerBand = VolatilityIndicators.calcBollingerBand(priceSeries, movingAverages);
        MomentumMetrics momentumMetrics = MomentumIndicators.calcMomentumMetrics(priceSeries);
        VolumeAnalysis volumeAnalysis = VolumeIndicators.calcVolumeAnalysis(priceSeries);
        PriceAction priceAction = PriceActionIndicators.calcPriceAction(priceSeries, movingAverages, momentumMetrics.adx());
        MacdResult macdResult = MomentumIndicators.calcMacd(priceSeries.closes());
        PivotSet pivotSet = PivotIndicators.calcPivotSet(dailyCharts, weeklyCharts);

        return MarketContext.builder()
                .chartDataType(chartDataType)
                .priceSeries(priceSeries)
                .movingAverages(movingAverages)
                .bollingerBand(bollingerBand)
                .momentum(momentumMetrics)
                .volume(volumeAnalysis)
                .priceAction(priceAction)
                .macd(macdResult)
                .pivots(pivotSet)
                .build();
    }
}