package com.stocklog.modules.stock.financial;

import com.stocklog.modules.stock.financial.dto.response.StockEarningResponse;
import com.stocklog.modules.stock.financial.dto.response.StockFinancialsResponse;
import com.stocklog.modules.stock.domain.type.MarketType;


public interface StockFinanceProvider {
    StockFinancialsResponse fetchFinancial(String symbol);
    StockEarningResponse fetchEarnings(String symbol);
    boolean supports(MarketType marketType);
}
