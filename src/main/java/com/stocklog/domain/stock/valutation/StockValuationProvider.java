package com.stocklog.domain.stock.valutation;

import com.stocklog.domain.stock.common.StockRequest;
import com.stocklog.domain.stock.common.type.MarketType;
import com.stocklog.domain.stock.valutation.dto.response.ValuationResponse;

public interface StockValuationProvider {
    ValuationResponse fetchValuation(StockRequest request);
     boolean supports(MarketType marketType);
}
