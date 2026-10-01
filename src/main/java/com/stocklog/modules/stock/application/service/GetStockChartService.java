package com.stocklog.modules.stock.application.service;

import com.stocklog.modules.stock.application.usecase.GetStockChartUseCase;
import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetStockChartService implements GetStockChartUseCase {

    private final StockChartService service;

    @Override
    public List<StockChartDto> getStockChartDtoList(String code) {
        return service.getDailyChartDtoDesc(code);
    }
}
