package com.stocklog.modules.stock.stock.dto;

import com.stocklog.modules.stock.application.response.StockMasterDto;
import com.stocklog.modules.stock.domain.type.Currency;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.stock.Stock;
import lombok.Builder;

@Builder
public record StockDto(
        String code,
        String standardCode,
        String name,
        MarketType market,
        Nation nation,
        Currency currency,
        String sector,
        String realtimeCode

) {
    public static StockDto from(Stock stock) {
        return StockDto.builder()
                .code(stock.getCode())
                .standardCode(stock.getStandardCode())
                .name(stock.getName())
                .market(stock.getMarket())
                .nation(stock.getNation())
                .currency(stock.getCurrency())
                .sector(stock.getSector())
                .realtimeCode(stock.getRealtimeCode())
                .build();
    }

    public static StockDto from(StockMasterDto stock) {
        return StockDto.builder()
                .code(stock.shortCode())
                .standardCode(stock.standardCode())
                .name(stock.korName())
                .market(stock.marketType())
                .nation(stock.nation())
                .currency(stock.currency())
                .sector(stock.sector())
                .realtimeCode(stock.rsym())
                .build();
    }
}
