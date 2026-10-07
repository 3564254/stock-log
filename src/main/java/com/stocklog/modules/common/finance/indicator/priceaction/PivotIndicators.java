package com.stocklog.modules.common.finance.indicator.priceaction;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.dto.CandleChartDto;
import com.stocklog.modules.common.finance.model.Pivot;
import com.stocklog.modules.common.finance.model.PivotSet;
import com.stocklog.modules.common.type.Period;

import java.util.List;

public final class PivotIndicators {

    private PivotIndicators() {}

    public static PivotSet calcPivotSet(List<? extends CandleChartDto> dailyCharts, List<? extends CandleChartDto> weeklyCharts) {
        if (MarketContext.invalidate(dailyCharts)) {
            return PivotSet.createFallback();
        }

        int lastIdx = dailyCharts.size() - 1;
        CandleChartDto yesterday = dailyCharts.get(lastIdx - 1);
        Pivot dailyPivot = calcPivotFromCandle(yesterday, Period.DAY);

        Pivot weeklyPivot = (weeklyCharts != null && weeklyCharts.size() >= 2)
                ? calcPivotFromCandle(weeklyCharts.get(weeklyCharts.size() - 2), Period.WEEK)
                : Pivot.createFallback(Period.WEEK);

        return new PivotSet(dailyPivot, weeklyPivot);
    }

    private static Pivot calcPivotFromCandle(CandleChartDto candle, Period period) {
        if (candle == null || candle.close() == null) {
            return Pivot.createFallback(period);
        }
        double close = candle.close().doubleValue();
        double high = candle.high() != null ? candle.high().doubleValue() : close;
        double low = candle.low() != null ? candle.low().doubleValue() : close;

        return calcPivotPoints(high, low, close, period);
    }

    public static Pivot calcPivotPoints(double high, double low, double close, Period period) {
        double p = (high + low + close) / 3.0;
        double range = high - low;

        return Pivot.builder()
                .p(p)
                .r1((2 * p) - low)
                .r2(p + range)
                .r3(high + 2 * (p - low))
                .s1((2 * p) - high)
                .s2(p - range)
                .s3(low - 2 * (high - p))
                .period(period)
                .build();
    }

    public static Pivot calcPivotPoints(double high, double low, double close) {
        return  PivotIndicators.calcPivotPoints(high, low, close, Period.DAY);

    }
}