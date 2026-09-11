package com.stocklog.modules.market.index.dto.vo;

import com.stocklog.modules.market.index.entity.MarketIndexHistory;
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

    public static IndexChartData createFallback() {
        return IndexChartData.builder()
                .date(null)
                .value(BigDecimal.ZERO)
                .build();
    }

    public static IndexChartData from(MarketIndexHistory history) {
        return IndexChartData.builder()
                .date(history.getDate())
                .value(history.getCloseValue())
                .build();
    }
}
