package com.stocklog.modules.market.open;

import com.stocklog.modules.common.type.Nation;

public interface MarketOpenProvider {
    MarketOpenInfo fetchMarketOpenInfo(Nation nation);
}
