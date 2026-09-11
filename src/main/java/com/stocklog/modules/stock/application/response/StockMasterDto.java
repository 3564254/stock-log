package com.stocklog.modules.stock.application.response;

import com.stocklog.modules.stock.domain.type.Currency;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.stock.domain.type.Nation;
import com.stocklog.modules.stock.stock.Stock;
import lombok.Builder;

@Builder
public record StockMasterDto(
    String shortCode,
    String standardCode,
    String korName,
    MarketType marketType,
    Nation nation,
    Currency currency,
    String sector,
    String rsym
){

    public static Stock toEntity(StockMasterDto masterDto) {
        return Stock.builder()
                .code(masterDto.shortCode())
                .standardCode(masterDto.standardCode())
                .name(masterDto.korName())
                .market(masterDto.marketType)
                .nation(masterDto.nation)
                .currency(masterDto.currency)
                .sector(masterDto.sector)
                .realtimeCode(masterDto.rsym)
                .build();
    }

    public static StockMasterDto from(Stock stock) {
        return StockMasterDto.builder()
                .shortCode(stock.getCode())
                .standardCode(stock.getStandardCode())
                .korName(stock.getName())
                .marketType(stock.getMarket())
                .nation(stock.getNation())
                .currency(stock.getCurrency())
                .sector(stock.getSector())
                .rsym(stock.getRealtimeCode())
                .build();
    }

}
