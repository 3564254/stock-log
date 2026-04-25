package com.stocklog.domain.stock.service;

import com.stocklog.domain.stock.dto.request.ChartRequest;
import com.stocklog.domain.stock.dto.response.StockChartResponse;
import com.stocklog.domain.stock.dto.vo.price.StockChartData;
import com.stocklog.domain.stock.entity.Stock;
import com.stocklog.domain.stock.provider.StockChartProvider;
import com.stocklog.domain.stock.type.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockChartService {
    private final StockService stockService;
    private final List<StockChartProvider> providers;

    public List<StockChartData> getChartData(String code, Period period) {
        Stock stock = stockService.getStock(code);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        ChartRequest request = new ChartRequest(stock.getCode(), stock.getMarketType(), date);

        StockChartResponse response = providers.stream()
                .filter(p -> p.supports(stock.getMarketType()))
                .findFirst()
                .map(p -> p.fetchInfo(request, period))
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 시장입니다."));

        return response.chartDataList().stream()
                .sorted(Comparator.comparing(StockChartData::getTime))
                .map(data -> StockChartData.builder()
                        .time(data.getTime())
                        .value(data.getValue())
                        .open(data.getOpen())
                        .high(data.getHigh())
                        .low(data.getLow())
                        .volume(data.getVolume())
                        .timeFrame(period)
                        .build())
                .toList();
    }
}
