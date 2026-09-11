package com.stocklog.modules.purchase.application.service;

import com.stocklog.modules.purchase.application.PortfolioDashboardResponse;
import com.stocklog.modules.purchase.application.usecase.PortfolioDashboardUseCase;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.portfolio.service.PortfolioFacadeService;
import com.stocklog.modules.purchase.trade.dto.response.TradingRuleResponse;
import com.stocklog.modules.purchase.trade.dto.response.WatchListResponse;
import com.stocklog.modules.purchase.trade.service.TradeFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PortfolioDashboardUseCase의 구현체 자리.
 */
@Service
@RequiredArgsConstructor
public class PortfolioDashboardService implements PortfolioDashboardUseCase {

    private final PortfolioFacadeService portfolioService;
    private final TradeFacadeService tradeService;

    @Override
    public PortfolioDashboardResponse getDashboardData(Long memberId) {
        PortfolioResponse portfolio = portfolioService.getMyPortfolio(memberId);
        WatchListResponse watchList = tradeService.getWatchlist(memberId);
        TradingRuleResponse tradingRules = tradeService.getActiveRules(memberId);

        return new PortfolioDashboardResponse(portfolio, watchList, tradingRules);
    }

}
