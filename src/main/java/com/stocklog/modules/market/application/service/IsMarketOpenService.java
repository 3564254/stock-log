package com.stocklog.modules.market.application.service;

import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.market.application.usecase.IsMarketOpenUseCase;
import com.stocklog.modules.market.open.MarketOpenInfo;
import com.stocklog.modules.market.open.MarketOpenService;
import com.stocklog.modules.market.open.MarketOpenType;
import com.stocklog.modules.stock.domain.type.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IsMarketOpenService implements IsMarketOpenUseCase {

    private final MarketOpenService calendarService;

    @Override
    public MarketOpenType isMarketOpen(Nation nation) {
        MarketOpenInfo marketCalendarDto = calendarService.getMarketOpenDto(nation);
        return marketCalendarDto.marketOpenType();
    }

    @Override
    public MarketOpenType isMarketOpen(MarketType marketType) {
        Nation nation = marketType.isDomestic() ? Nation.KR : Nation.US;
        return isMarketOpen(nation);
    }
}
