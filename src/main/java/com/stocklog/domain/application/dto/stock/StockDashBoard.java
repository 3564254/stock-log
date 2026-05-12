package com.stocklog.domain.application.dto.stock;

import com.stocklog.domain.stock.price.dto.internal.StockPriceDto;
import com.stocklog.domain.stock.stock.dto.StockDto;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record StockDashBoard(
        StockDto stock,
        BigDecimal currentPrice,
        BigDecimal changeRate
) {
    public static StockDashBoard of(StockDto stock, StockPriceDto priceDto) {
        return StockDashBoard.builder()
                .stock(stock)
                .currentPrice(priceDto.currentPrice())
                .changeRate(priceDto.changeRate())
                .build();
    }
}
