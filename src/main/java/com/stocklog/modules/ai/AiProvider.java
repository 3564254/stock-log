package com.stocklog.modules.ai;

import com.stocklog.modules.purchase.portfolio.analysis.dto.PortfolioAnalysisResult;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;



public interface AiProvider {
    String getStockAnalysis(StockAnalysisResult analysisResult);

    String getPortfolioAnalysis(PortfolioAnalysisResult analysisResult);
}