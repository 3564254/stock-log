package com.stocklog.domain.stock.chart.dto.internal;

import com.stocklog.domain.stock.chart.StockChart;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.stocklog.global.util.FinancialConst;

@Builder
public record StockChartDto(
        LocalDate time,
        BigDecimal open,
        BigDecimal close,
        BigDecimal high,
        BigDecimal low,
        BigDecimal volume
)
{

    public static StockChartDto createFallback() {
        return StockChartDto.builder()
                .time(null)
                .open(BigDecimal.ZERO)
                .close(BigDecimal.ZERO)
                .high(BigDecimal.ZERO)
                .low(BigDecimal.ZERO)
                .volume(BigDecimal.ZERO)
                .build();
    }

    public static StockChartDto from(StockChart chart) {
        return StockChartDto.builder()
                .time(chart.getTradeDate())
                .open(chart.getOpenPrice())
                .close(chart.getClosePrice())
                .high(chart.getHighPrice())
                .low(chart.getLowPrice())
                .volume(chart.getVolume())
                .build();
    }


    public static String formatDate(String rawDate) {
        return rawDate.substring(0, 4) + "-" +
                rawDate.substring(4, 6) + "-" +
                rawDate.substring(6, 8);
    }
}

