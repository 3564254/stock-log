package com.stocklog.modules.stock.price;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.price.dto.StockPriceResponse;
import com.stocklog.modules.stock.domain.type.MarketType;

public interface StockPriceProvider {
    StockPriceResponse fetchPrice(StockRequest request);
    boolean supports(MarketType marketType);
}
