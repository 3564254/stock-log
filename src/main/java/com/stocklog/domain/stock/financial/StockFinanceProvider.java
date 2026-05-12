package com.stocklog.domain.stock.financial;

import com.stocklog.domain.stock.financial.dto.response.StockEarningResponse;
import com.stocklog.domain.stock.financial.dto.response.StockFinancialsResponse;
import com.stocklog.domain.stock.common.type.MarketType;


public interface StockFinanceProvider {
    StockFinancialsResponse fetchFinancial(String symbol);
    StockEarningResponse fetchEarnings(String symbol);
    boolean supports(MarketType marketType);
}
