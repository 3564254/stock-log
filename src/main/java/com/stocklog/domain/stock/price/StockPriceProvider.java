package com.stocklog.domain.stock.price;

import com.stocklog.domain.stock.common.StockRequest;
import com.stocklog.domain.stock.price.dto.response.StockPriceResponse;
import com.stocklog.domain.stock.common.type.MarketType;

public interface StockPriceProvider {
    StockPriceResponse fetchPrice(StockRequest request);
    boolean supports(MarketType marketType);
}
