package com.stocklog.modules.common.finance.series;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.dto.CandleChartDto;
import com.stocklog.modules.common.finance.dto.SimpleChartDto;
import jakarta.annotation.Nullable;
import lombok.Builder;

import java.util.List;

@Builder
public record PriceSeries(
        List<? extends SimpleChartDto> dailyCharts,
        List<? extends SimpleChartDto> weeklyCharts,
        int lastIdx,
        List<Double> closes,
        @Nullable List<Double> highs,
        @Nullable List<Double> lows,
        @Nullable List<Double> volumes,
        double currentPrice,
        double yesterdayPrice
) {
    public static PriceSeries ofCandle(List<? extends CandleChartDto> dailyCharts, List<? extends CandleChartDto> weeklyCharts) {
        if (MarketContext.invalidate(dailyCharts)) return null;

        List<Double> closes = dailyCharts.stream().map(b -> b.close().doubleValue()).toList();
        List<Double> highs = dailyCharts.stream().map(b -> b.high().doubleValue()).toList();
        List<Double> lows = dailyCharts.stream().map(b -> b.low().doubleValue()).toList();
        List<Double> volumes = dailyCharts.stream().map(b -> b.volume() != null ? b.volume().doubleValue() : 0.0).toList();

        int lastIdx = closes.size() - 1;
        double cur = closes.get(lastIdx);
        double prev = closes.get(lastIdx - 1);

        return PriceSeries.builder()
                .dailyCharts(dailyCharts)
                .weeklyCharts(weeklyCharts)
                .lastIdx(lastIdx)
                .closes(closes)
                .highs(highs)
                .lows(lows)
                .volumes(volumes)
                .currentPrice(cur)
                .yesterdayPrice(prev)
                .build();
    }

    public static PriceSeries ofSimple(List<? extends SimpleChartDto> dailyCharts, List<? extends SimpleChartDto> weeklyCharts) {
        if (MarketContext.invalidate(dailyCharts)) return null;

        List<Double> closes = dailyCharts.stream().map(b -> b.close().doubleValue()).toList();
        int lastIdx = closes.size() - 1;
        double cur = closes.get(lastIdx);
        double prev = closes.get(lastIdx - 1);

        return PriceSeries.builder()
                .dailyCharts(dailyCharts)
                .weeklyCharts(weeklyCharts)
                .lastIdx(lastIdx)
                .closes(closes)
                .currentPrice(cur)
                .yesterdayPrice(prev)
                .build();

    }

    public static PriceSeries createFallback() {
        return PriceSeries.builder()
                .dailyCharts(List.of())
                .weeklyCharts(List.of())
                .lastIdx(0)
                .closes(List.of())
                .currentPrice(0)
                .yesterdayPrice(0)
                .build();
    }




}
