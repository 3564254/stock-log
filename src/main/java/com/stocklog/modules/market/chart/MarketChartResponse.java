package com.stocklog.modules.market.chart;

import com.stocklog.modules.market.index.type.IndexType;
import lombok.Builder;

import java.util.List;

@Builder
public record MarketChartResponse(
        List<MarketChartDto> marketChartDtoList
) {
    public static MarketChartResponse createFallback() {
        return MarketChartResponse.builder()
                .marketChartDtoList(List.of())
                .build();
    }
}
