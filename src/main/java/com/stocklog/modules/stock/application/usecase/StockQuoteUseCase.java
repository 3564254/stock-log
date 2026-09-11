package com.stocklog.modules.stock.application.usecase;


import com.stocklog.modules.stock.application.response.StockQuote;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StockQuoteUseCase {
    StockQuote getStockQuote(String code);
    List<StockQuote> getStockQuoteListByKeyword(String keyword, Pageable pageable);
}
