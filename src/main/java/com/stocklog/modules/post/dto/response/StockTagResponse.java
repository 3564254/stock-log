package com.stocklog.modules.post.dto.response;

import com.stocklog.modules.stock.stock.Stock;

public record StockTagResponse(
        String code,
        String name
) {
    public static StockTagResponse from(Stock stock) {
        return new StockTagResponse(stock.getCode(), stock.getName());
    }
}
