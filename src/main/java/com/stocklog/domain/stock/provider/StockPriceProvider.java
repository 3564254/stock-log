package com.stocklog.domain.stock.provider;

import com.stocklog.domain.stock.dto.request.StockRequest;
import com.stocklog.domain.stock.dto.vo.price.StockPriceDetail;
import com.stocklog.domain.stock.type.MarketType;

public interface StockPriceProvider {
    StockPriceDetail fetchPrice(StockRequest request);
    boolean supports(MarketType marketType);
}
