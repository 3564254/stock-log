package com.stocklog.domain.stock.chart.dto.internal;

import com.stocklog.domain.stock.chart.dto.response.StockChartResponse;
import com.stocklog.domain.stock.common.type.Period;
import lombok.Builder;

import java.util.List;

@Builder
public record StockChartListDto (
        String code,
        List<StockChartDto> chartDtoList,
        Period period
){
    public static StockChartListDto createFallback() {
        return StockChartListDto.builder()
                .code("")
                .chartDtoList(List.of())
                .period(null)
                .build();
    }

}
