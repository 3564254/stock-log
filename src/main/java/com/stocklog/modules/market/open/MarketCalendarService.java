package com.stocklog.modules.market.open;

import com.stocklog.modules.common.type.Nation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketCalendarService {
    private final MarketCalendarProvider marketCalendarProvider;

    public MarketCalendarResponse getMarketCalendarResponse() {
        return MarketCalendarResponse.builder()
                .KrMarketCalendar(marketCalendarProvider.fetchMarketOpenInfo(Nation.KR))
                .UsMarketCalendar(marketCalendarProvider.fetchMarketOpenInfo(Nation.US))
                .build();
    }







}
