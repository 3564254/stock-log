package com.stocklog.modules.market.open;

import com.stocklog.modules.common.type.Nation;

public interface MarketCalendarProvider {
    MarketCalendarInfo fetchMarketOpenInfo(Nation nation);
}
