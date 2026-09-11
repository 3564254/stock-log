package com.stocklog.modules.purchase.application;

import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.trade.dto.response.TradingRuleResponse;
import com.stocklog.modules.purchase.trade.dto.response.WatchListResponse;


public record PortfolioDashboardResponse(
        PortfolioResponse portfolio,         // 1. 전략 가동 중인 종목 (계좌 평균 수익률, 보유 종목 등)
        WatchListResponse watchList,        // 2. 관심 종목 (진입 대기 큐)
        TradingRuleResponse tradingRules    // 3. 활성화된 매매 규칙
) {
}