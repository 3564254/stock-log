package com.stocklog.domain.stock.info;

import com.stocklog.domain.stock.common.StockRequest;
import com.stocklog.domain.stock.info.dto.response.StockInfoResponse;
import com.stocklog.domain.stock.common.type.MarketType;

public interface StockInfoProvider {
    StockInfoResponse fetchInfo(StockRequest request);
    boolean supports(MarketType marketType);
}
