package com.stocklog.domain.marketdata.index.dto;

import com.stocklog.domain.marketdata.index.dto.vo.IndexChartData;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Builder
public record IndexResponse(
        String seriesId,
        List<IndexChartData> data,
        BigDecimal changeValue,
        BigDecimal changeRate
) {
    public static IndexResponse createFallback() {
        return IndexResponse.builder()
                .seriesId("")
                .data(List.of())
                .changeValue(BigDecimal.ZERO)
                .changeRate(BigDecimal.ZERO)
                .build();
    }
}