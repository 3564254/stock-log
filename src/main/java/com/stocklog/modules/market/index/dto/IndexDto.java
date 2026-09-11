package com.stocklog.modules.market.index.dto;

import com.stocklog.modules.market.index.dto.vo.IndexChartData;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record IndexDto(
        String seriesId,
        List<IndexChartData> data,
        BigDecimal changeValue,
        BigDecimal changeRate
) {
    public static IndexDto createFallback() {
        return IndexDto.builder()
                .seriesId("")
                .data(List.of())
                .changeValue(BigDecimal.ZERO)
                .changeRate(BigDecimal.ZERO)
                .build();
    }
}