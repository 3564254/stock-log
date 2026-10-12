package com.stocklog.modules.market.open;

import lombok.Builder;

@Builder
public record MarketCalendarResponse(
        MarketCalendarInfo KrMarketCalendar,
        MarketCalendarInfo UsMarketCalendar
) {
}

