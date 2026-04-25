package com.stocklog.domain.stock.dto.internal;

import com.stocklog.domain.stock.type.Currency;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.domain.stock.type.Nation;
import com.stocklog.domain.stock.entity.Stock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
public record StockLoadDto (
    String shortCode,
    String standardCode,
    String korName,
    MarketType marketType,
    Nation nation,
    Currency currency,
    String sector,
    String rsym
){
    public Stock toEntity() {
        return Stock.builder()
                .code(shortCode)
                .standardCode(standardCode)
                .name(korName)
                .marketType(marketType)
                .nation(nation)
                .currency(currency)
                .sector(sector)
                .realtimeCode(rsym != null ? rsym : shortCode)
                .build();
    }

}
