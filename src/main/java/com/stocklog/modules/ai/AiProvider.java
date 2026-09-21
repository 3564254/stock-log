package com.stocklog.modules.ai;

import com.stocklog.modules.market.analysis.dto.EngineInput;
import com.stocklog.modules.market.news.dto.NewsDto;
import com.stocklog.modules.purchase.portfolio.analysis.dto.PortfolioAnalysisResult;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;

import java.util.List;


public interface AiProvider {
    String getStockAnalysis(StockAnalysisResult analysisResult);

    String getPortfolioAnalysis(PortfolioAnalysisResult analysisResult);

    String getMarketAnalysis(EngineInput input, List<NewsDto> newsList);
}