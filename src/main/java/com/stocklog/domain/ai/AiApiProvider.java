package com.stocklog.domain.ai;

import com.stocklog.domain.portfolio.analysis.dto.PortfolioAnalysisResult;
import com.stocklog.domain.stock.analysis.result.StockAnalysisResult;

public interface AiApiProvider {
    String getStockAnalysis(StockAnalysisResult analysisResult);

    String getPortfolioAnalysis(PortfolioAnalysisResult analysisResult);
}
