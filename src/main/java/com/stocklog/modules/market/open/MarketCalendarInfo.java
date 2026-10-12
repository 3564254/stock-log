package com.stocklog.modules.market.open;

import com.stocklog.modules.common.type.Nation;

import java.time.LocalDateTime;

public record MarketCalendarInfo(
        Nation nation,
        MarketOpenType marketOpenType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean isMarketOpen
) {
}
