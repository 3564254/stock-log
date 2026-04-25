package com.stocklog.domain.stock.dto.response;

import com.stocklog.domain.stock.dto.vo.price.StockPrice; // 시세 VO 사용 권장
import lombok.Builder;

@Builder
public record StockPriceResponse(
        // [1. 변하지 않는 정보]
        StockResponse stock,

        // [2. 실시간으로 변하는 시세 정보]
        StockPrice price
) {
    public static StockPriceResponse of(StockResponse stock, StockPrice price) {
        return StockPriceResponse.builder()
                .stock(stock)
                .price(price)
                .build();
    }

    public static StockPriceResponse createFallback() {
        return StockPriceResponse.builder()
                .stock(StockResponse.createFallback())
                .price(StockPrice.createFallback())
                .build();
    }
}