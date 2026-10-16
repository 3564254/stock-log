package com.stocklog.modules.market.service;

import com.stocklog.modules.market.analysis.dto.MarketRegimeDto;
import com.stocklog.modules.market.analysis.dto.MarketRegimeResponse;
import com.stocklog.modules.market.analysis.dto.TotalResult;
import com.stocklog.modules.market.analysis.service.MarketAnalysisService;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisDto;
import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisService;
import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.index.dto.IndexDashboardDto;
import com.stocklog.modules.market.index.dto.IndexDto;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.market.news.dto.NewsResponse;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class MarketFacadeService {

    private final ExchangeService exchangeService;
    private final IndexService indexService;
    private final NewsService newsService;
    private final MarketAnalysisService analysisService;
    private final MarketDiagnosisService diagnosisService;
    private final com.stocklog.modules.market.analysis.service.horizon.HorizonService horizonService;

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
    public MarketRegimeDto getMarketAnalysisDto() {
        return analysisService.analyzeMarket();
    }

    public MarketRegimeResponse getMarketAnalysisResponse(int n) {
        TotalResult totalResult = analysisService.getTotalResult();
        MarketDiagnosisDto marketDiagnosisDto = diagnosisService.getMarketDiagnosisDto(totalResult.engineInput(),
                totalResult.marketRegimeDto(),
                newsService.getMarketNewsDESCTopN(n));
        return MarketRegimeResponse.builder()
                .marketRegimeDto(totalResult.marketRegimeDto())
                .diagnosisDto(marketDiagnosisDto)
                .dataDate(totalResult.dataDate())
                .horizons(safeHorizons(totalResult))
                .build();

    }



    // 시간축 판정이 실패해도 시장 진단 전체는 보여 준다
    private com.stocklog.modules.market.analysis.service.horizon.HorizonService.HorizonReport safeHorizons(TotalResult totalResult) {
        try {
            return horizonService.evaluate(totalResult.engineInput());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(MarketFacadeService.class).warn("[시장 진단] 시간축 판정 실패: {}", e.getMessage());
            return null;
        }
    }

    // ============== News Service ============== //
    public NewsResponse fetchStockNews(StockDto stockDto, Pageable pageable) {
        return newsService.fetchStockNews(stockDto, pageable);
    }

    public NewsResponse fetchMarketNews(Pageable pageable) {
        return newsService.getMarketNews(pageable);
    }

    public List<NewsDto> fetchKoreanHeadlines(int n) {
        return newsService.getKoreanHeadlines(n);
    }

    public List<NewsDto> fetchNewsDtoList(int n) {
        return newsService.getMarketNewsDESCTopN(n);
    }

}