package com.stocklog.modules.stock.chart.dto;

import com.stocklog.modules.common.finance.vo.CandleChartDto;
import com.stocklog.modules.stock.chart.StockChart;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record StockChartDto(
        LocalDate date,
        BigDecimal open,
        BigDecimal close,
        BigDecimal high,
        BigDecimal low,
        BigDecimal volume
) implements CandleChartDto
{

    public static StockChartDto createFallback() {
        return StockChartDto.builder()
                .date(null)
                .open(BigDecimal.ZERO)
                .close(BigDecimal.ZERO)
                .high(BigDecimal.ZERO)
                .low(BigDecimal.ZERO)
                .volume(BigDecimal.ZERO)
                .build();
    }

    public static StockChartDto from(StockChart chart) {
        return StockChartDto.builder()
                .date(chart.getTradeDate())
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

    public double closeDoubleValue() {
        return close.doubleValue();
    }
}

