package com.stocklog.modules.market.chart;

import lombok.Builder;

import java.util.List;

@Builder
public record MarketChartResponse(
        List<MarketChartData> marketChartDataList
) {
    public static MarketChartResponse createFallback() {
        return MarketChartResponse.builder()
                .marketChartDataList(List.of())
                .build();
    }
}
