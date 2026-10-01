package com.stocklog.modules.market.index.dto;

import com.stocklog.modules.common.finance.dto.SimpleChartDto;
import com.stocklog.modules.market.index.entity.MarketIndexHistory;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record IndexChartDto(
    LocalDate date,
    BigDecimal close
) implements SimpleChartDto
{

    public static IndexChartDto createFallback() {
        return IndexChartDto.builder()
                .date(null)
                .close(BigDecimal.ZERO)
                .build();
    }

    public static IndexChartDto from(MarketIndexHistory history) {
        return IndexChartDto.builder()
                .date(history.getDate())
                .close(history.getCloseValue())
                .build();
    }

    public Double getDouble() {
        return close.doubleValue();
    }
}
