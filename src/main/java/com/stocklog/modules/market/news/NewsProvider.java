package com.stocklog.modules.market.news;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.market.news.dto.NewsDto;

import java.util.List;

public interface NewsProvider {
    List<NewsDto> fetchStockNews(StockRequest stockRequest);

    List<NewsDto> fetchMarketNews();
    boolean supports(MarketType marketType);

    /** 저장할 때 붙이는 출처. 구현체마다 재정의한다 */
    default NewsSource source() {
        return NewsSource.UNKNOWN;
    }
}
