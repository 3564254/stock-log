package com.stocklog.domain.stock.provider;

import com.stocklog.domain.stock.dto.request.StockRequest;
import com.stocklog.domain.stock.dto.vo.info.StockInfoDetail;
import com.stocklog.domain.stock.type.MarketType;

public interface StockInfoProvider {
    StockInfoDetail fetchInfo(StockRequest request);
    boolean supports(MarketType marketType);
}
