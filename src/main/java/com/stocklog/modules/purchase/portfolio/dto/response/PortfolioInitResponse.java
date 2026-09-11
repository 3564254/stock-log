package com.stocklog.modules.purchase.portfolio.dto.response;

import com.stocklog.modules.purchase.trade.dto.response.WatchListResponse;
import com.stocklog.modules.purchase.trade.dto.vo.TradingRuleDto;
import lombok.Builder;

@Builder
public record PortfolioInitResponse(
        WatchListResponse watchListResponse,
        TradingRuleDto tradingRuleDto
) {
}
