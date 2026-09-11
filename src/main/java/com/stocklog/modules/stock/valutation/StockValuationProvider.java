package com.stocklog.modules.stock.valutation;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.stock.valutation.dto.ValuationResponse;

public interface StockValuationProvider {
    ValuationResponse fetchValuation(StockRequest request);
     boolean supports(MarketType marketType);
}
