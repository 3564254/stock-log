package com.stocklog.modules.stock.application.response;

import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record StockQuote(
        StockDto stock,
        BigDecimal currentPrice,
        BigDecimal changeRate
) {
    public static StockQuote of(StockDto stock, StockPriceDto priceDto) {
        return StockQuote.builder()
                .stock(stock)
                .currentPrice(priceDto.currentPrice())
                .changeRate(priceDto.changeRate())
                .build();
    }
}
