package com.stocklog.modules.stock.application.service;

import com.stocklog.modules.stock.application.response.StockSummary;
import com.stocklog.modules.stock.application.usecase.StockSummaryUseCase;
import com.stocklog.modules.stock.info.StockInfoService;
import com.stocklog.modules.stock.price.StockPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockSummaryService implements StockSummaryUseCase {

    private final StockInfoService infoService;
    private final StockPriceService priceService;

    @Override
    public StockSummary getStockSummary(String code) {
        return StockSummary.builder()
                .info(infoService.fetchInfoDto(code))
                .price(priceService.fetchPrice(code))
                .build();
    }
}
