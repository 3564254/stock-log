package com.stocklog.modules.market.service;

import com.stocklog.modules.market.analysis.dto.MarketAnalysisResultResponse;
import com.stocklog.modules.market.analysis.MarketAnalysisService;
import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.index.dto.IndexDashboardDto;
import com.stocklog.modules.market.index.dto.IndexDto;
import com.stocklog.modules.market.news.dto.NewsResponse;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class MarketFacadeService {
    private final ExchangeService exchangeService;
    private final IndexService indexService;
    private final NewsService newsService;
    private final MarketAnalysisService analysisService;

    // ============== Exchange Service ============== //
    public ExchangeRateInfo fetchExchangeRate() {
        return exchangeService.fetchExchangeRate();
    }

    // ============== Index Service ============== //

    public IndexDashboardDto getMarketDashboard() {
        return indexService.getMarketDashboard();
    }

    public IndexDto getIndexDto(String seriesId) {
        return indexService.getIndexDtoFromDb(seriesId);
    }

    // ============== analysis Service ============== //
    public MarketAnalysisResultResponse getMarketAnalysis(IndexDashboardDto dashboardDto) {
        return analysisService.calculateMarketDiagnosis(dashboardDto);
    }

    // ============== News Service ============== //
    public NewsResponse fetchStockNews(StockDto stockDto, Pageable pageable) {
        return newsService.fetchStockNews(stockDto, pageable);
    }

    public NewsResponse fetchMarketNews(Pageable pageable) {
        return newsService.getMarketNews(pageable);
    }

}