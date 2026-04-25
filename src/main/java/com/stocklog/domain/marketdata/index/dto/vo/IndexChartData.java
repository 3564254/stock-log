package com.stocklog.domain.marketdata.index.dto.vo;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class IndexChartData {
    private LocalDate date;
    private BigDecimal value;

    public IndexChartData createFallback() {
        return IndexChartData.builder()
                .date(null)
                .value(BigDecimal.ZERO)
                .build();

    }
}
