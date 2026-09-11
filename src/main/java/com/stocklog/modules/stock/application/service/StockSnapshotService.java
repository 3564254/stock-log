package com.stocklog.modules.stock.application.service;

import com.stocklog.modules.stock.application.response.StockSnapshot;
import com.stocklog.modules.stock.application.usecase.StockSnapshotUseCase;
import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.financial.StockFinancialService;
import com.stocklog.modules.stock.info.StockInfoService;
import com.stocklog.modules.stock.price.StockPriceService;
import com.stocklog.modules.stock.valutation.StockValuationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockSnapshotService implements StockSnapshotUseCase {

    private final StockInfoService infoService;
    private final StockPriceService priceService;
    private final StockFinancialService financialService;
    private final StockValuationService valuationService;
    private final StockChartService chartService;


    @Override
    public StockSnapshot getStockSnapshot(String code) {
        return StockSnapshot.builder()
                .info(infoService.fetchInfoDto(code))
                .price(priceService.fetchPrice(code))
                .financial(financialService.fetchFinancial(code))
                .valuation(valuationService.fetchValuation(code))
                .dailyCharts(chartService.getDailyChartDtoDesc(code))
                .weeklyCharts(chartService.getWeeklyChartDtoDesc(code))
                .build();
    }
}
