package com.stocklog.modules.market.application.service;

import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.market.application.usecase.IsMarketOpenUseCase;
import com.stocklog.modules.market.open.MarketCalendarInfo;
import com.stocklog.modules.market.open.MarketCalendarService;
import com.stocklog.modules.market.open.MarketOpenType;
import com.stocklog.modules.stock.domain.type.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IsMarketOpenService implements IsMarketOpenUseCase {

    private final MarketCalendarService calendarService;

    @Override
    public MarketOpenType isMarketOpen(Nation nation) {
        MarketCalendarInfo marketCalendarDto = calendarService.getMarketCalendarDto(nation);
        return marketCalendarDto.marketOpenType();
    }

    @Override
    public MarketOpenType isMarketOpen(MarketType marketType) {
        Nation nation = marketType.isDomestic() ? Nation.KR : Nation.US;
        return isMarketOpen(nation);
    }
}
