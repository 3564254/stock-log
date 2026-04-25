package com.stocklog.web.api;

import com.stocklog.domain.marketdata.exchange.dto.ExchangeRateInfo;
import com.stocklog.domain.marketdata.exchange.service.ExchangeService;
import com.stocklog.domain.marketdata.index.dto.IndexResponse;
import com.stocklog.domain.marketdata.index.service.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketData")
@RequiredArgsConstructor
@Slf4j
public class MarketDataApiController {
    private final ExchangeService exchangeService;
    private final IndexService indexService;

    @GetMapping("/exchange")
    public ExchangeRateInfo getExchangeRate() {
        return exchangeService.fetchExchangeRate();
    }

    @GetMapping("/{seriesId}/index")
    public IndexResponse getIndexResponse(@PathVariable String seriesId) {
        return indexService.getIndexResponse(seriesId);
    }
}
