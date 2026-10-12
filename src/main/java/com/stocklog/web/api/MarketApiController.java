package com.stocklog.web.api;

import com.stocklog.infra.toss.exchange.TossExchangeRateProvider;
import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.exchange.provider.ExchangeRateProvider;
import com.stocklog.modules.market.index.dto.IndexDto;
import com.stocklog.modules.market.service.MarketFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketData")
@RequiredArgsConstructor
@Slf4j
public class MarketApiController {

    private final MarketFacadeService marketFacadeService;

    @GetMapping("/exchange")
    public ExchangeRateInfo getExchangeRate() {
        return marketFacadeService.fetchExchangeRate();
    }

    @GetMapping("/{seriesId}/index")
    public IndexDto getIndexResponse(@PathVariable String seriesId) {
        return marketFacadeService.getIndexDto(seriesId);
    }


}
