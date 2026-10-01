package com.stocklog.modules.common.finance.model;

import com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators;
import lombok.Builder;

import java.util.List;

@Builder
public record MacdResult(
        double macd,
        double signal,
        double histogram,
        boolean isGoldenCross,
        boolean isDeadCross,
        boolean isHistogramRising
) {
    public static MacdResult createFallback() {
        return MacdResult.builder()
                .macd(0)
                .signal(0)
                .histogram(0)
                .isGoldenCross(false)
                .isDeadCross(false)
                .isHistogramRising(false)
                .build();
    }

    public static MacdResult from(List<Double> prices) {
        return MomentumIndicators.calcMacd(prices);
    }
}
