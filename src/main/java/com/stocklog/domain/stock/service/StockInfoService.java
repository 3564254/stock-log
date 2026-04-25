package com.stocklog.domain.stock.service;

import com.stocklog.domain.stock.dto.request.StockRequest;
import com.stocklog.domain.stock.dto.response.StockInfoResponse;
import com.stocklog.domain.stock.dto.response.StockResponse;
import com.stocklog.domain.stock.dto.vo.info.StockInfoDetail;
import com.stocklog.domain.stock.entity.Stock;
import com.stocklog.domain.stock.provider.StockInfoProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockInfoService {

    private final StockService stockService;
    private final List<StockInfoProvider> providers;


    public StockInfoResponse fetchInfo(String code) {
        Stock stock = stockService.getStock(code);
        StockInfoDetail stockInfoDetail = providers.stream()
                .filter(providers -> providers.supports(stock.getMarketType()))
                .findFirst()
                .map(provider -> provider.fetchInfo(StockRequest.from(stock)))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다: " + code));
        return StockInfoResponse.of(StockResponse.from(stock), stockInfoDetail);
    }

    public StockInfoDetail fetchInfoDetail(String code) {
        Stock stock = stockService.getStock(code);

        return providers.stream()
                .filter(p -> p.supports(stock.getMarketType()))
                .findFirst()
                .map(p -> p.fetchInfo(StockRequest.from(stock)))
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 시장 타입입니다: " + stock.getMarketType()));
    }
}
