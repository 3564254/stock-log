package com.stocklog.domain.stock.dto.response;

import com.stocklog.domain.stock.entity.Stock;
import com.stocklog.domain.stock.type.Currency;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.domain.stock.type.Nation;
import lombok.Builder;

/**
 * 주식 기본 정보 응답 (정적 데이터)
 */
@Builder
public record StockResponse(
        String code,
        String name,
        Nation nation,
        String sector,
        MarketType market,
        Currency currency
) {
    public static StockResponse from(Stock stock) {
        return StockResponse.builder()
                .code(stock.getCode())
                .name(stock.getName())
                .nation(stock.getNation())
                .sector(stock.getSector())
                .market(stock.getMarketType())
                .currency(stock.getCurrency())
                .build();
    }

    public static StockResponse createFallback() {
        return StockResponse.builder()
                .code("")
                .name("")
                .nation(Nation.UNKNOWN)
                .sector("")
                .market(MarketType.UNKNOWN)
                .currency(Currency.UNKNOWN)
                .build();
    }
}