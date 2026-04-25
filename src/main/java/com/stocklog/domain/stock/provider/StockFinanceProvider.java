package com.stocklog.domain.stock.provider;

import com.stocklog.domain.stock.dto.response.StockEarningResponse;
import com.stocklog.domain.stock.dto.response.StockFinancialsResponse;
import com.stocklog.domain.stock.type.MarketType;


public interface StockFinanceProvider {
    StockFinancialsResponse getFinancialStatements(String symbol);
    StockEarningResponse getEarningsSurprises(String symbol);
    boolean supports(MarketType marketType);
}
