package com.stocklog.domain.stock.dto.request;

import com.stocklog.domain.stock.entity.Stock;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.domain.stock.type.Nation;
import lombok.Data;

public record StockRequest(
        String code,
        MarketType marketType,
        Nation nation
) {
    public static StockRequest of(String code, MarketType marketType, Nation nation) {
        return new StockRequest(code, marketType, nation);
    }

    public static StockRequest from(Stock stock) {
        return new StockRequest(
                stock.getCode(),
                stock.getMarketType(),
                stock.getNation()
        );
    }
}
