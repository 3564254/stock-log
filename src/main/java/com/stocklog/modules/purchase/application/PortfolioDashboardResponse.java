package com.stocklog.modules.purchase.application;

import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.trade.dto.response.TradingRuleResponse;
import com.stocklog.modules.purchase.trade.dto.response.WatchListResponse;


public record PortfolioDashboardResponse(
        PortfolioResponse portfolio,         // 전략 가동 중인 종목
        WatchListResponse watchList,        // 관심 종목 (진입 대기 큐)
        TradingRuleResponse tradingRules    // 활성화된 매매 규칙
) {
}