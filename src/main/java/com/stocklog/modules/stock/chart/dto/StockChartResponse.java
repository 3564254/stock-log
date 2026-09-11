package com.stocklog.modules.stock.chart.dto;

import com.stocklog.modules.stock.domain.type.Period;
import lombok.Builder;

import java.util.List;
@Builder
public record StockChartResponse(
        String code,
        List<StockChartDto> chartDtoList,
        Period period

) {
    public static StockChartResponse createFallback() {
        return StockChartResponse.builder()
                .code("")
                .chartDtoList(List.of())
                .period(null)
                .build();
    }
}
