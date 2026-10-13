package com.stocklog.modules.market.application.usecase;

import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.market.open.MarketOpenType;
import com.stocklog.modules.stock.domain.type.MarketType;

public interface IsMarketOpenUseCase {
    MarketOpenType isMarketOpen(Nation nation);
    MarketOpenType isMarketOpen(MarketType marketType);
}
