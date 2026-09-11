package com.stocklog.modules.market.news;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.market.news.dto.NewsDto;

import java.util.List;

public interface NewsProvider {
    List<NewsDto> fetchStockNews(StockRequest stockRequest);

    List<NewsDto> fetchMarketNews();
    boolean supports(MarketType marketType);
}
