package com.stocklog.modules.stock.info;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.info.dto.StockInfoResponse;
import com.stocklog.modules.stock.domain.type.MarketType;

public interface StockInfoProvider {
    StockInfoResponse fetchInfo(StockRequest request);
    boolean supports(MarketType marketType);
}
