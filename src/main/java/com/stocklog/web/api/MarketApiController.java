package com.stocklog.web.api;

import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.index.dto.IndexDto;
import com.stocklog.modules.market.service.MarketFacadeService;
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

//    @GetMapping("/dashboard")
//    public ResponseEntity<MarketOverviewDto> getMarketDashboard() {
//        log.info("[API] 시황 대시보드 조회 요청");
//
//        // 퍼사드 서비스에서 모든 데이터를 조립해서 가져옴
//        MarketOverviewDto response = stockFacadeService.getComprehensiveMarketDashboard();
//
//        return ResponseEntity.ok(response);
//    }
//
//    @GetMapping("/{stockId}/news")
//    public StockNewsResponse getStockNewsResponse(@PathVariable String stockId) {
//        return marketFacadeService.getStockNewsResponse(stockId, LocalDate.now());
//    }
}
