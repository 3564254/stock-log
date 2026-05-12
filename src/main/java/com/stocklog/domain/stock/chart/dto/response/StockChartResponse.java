package com.stocklog.domain.stock.chart.dto.response;

import com.stocklog.domain.stock.chart.dto.internal.StockChartDto;
import com.stocklog.domain.stock.common.type.Period;
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
