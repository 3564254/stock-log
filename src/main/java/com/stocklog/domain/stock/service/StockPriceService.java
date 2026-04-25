package com.stocklog.domain.stock.service;

import com.stocklog.domain.stock.dto.request.StockRequest;
import com.stocklog.domain.stock.dto.response.StockResponse;
import com.stocklog.domain.stock.dto.response.StockPriceResponse;
import com.stocklog.domain.stock.dto.vo.price.StockPriceDetail;
import com.stocklog.domain.stock.entity.Stock;
import com.stocklog.domain.stock.provider.StockPriceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockPriceService {

    private final StockService stockService;
    private final List<StockPriceProvider> providers;


    public StockPriceResponse fetchPrice(String code) {
        Stock stock = stockService.getStock(code);
        StockPriceDetail stockPriceDetail = providers.stream()
                .filter(providers -> providers.supports(stock.getMarketType()))
                .findFirst()
                .map(provider -> provider.fetchPrice(StockRequest.from(stock)))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다: " + code));
        log.info("[{}] per: {}", code, stockPriceDetail.getPer());
        return StockPriceResponse.of(StockResponse.from(stock), stockPriceDetail);
    }

    public List<StockPriceResponse> fetchPriceByKeyword(String keyword, Pageable pageable) {
        Slice<Stock> stockList = stockService.searchStocks(keyword, pageable);

        List<StockPriceResponse> responses = new ArrayList<>();
        for (Stock stock : stockList) {
            for (StockPriceProvider provider : providers) {
                if (provider.supports(stock.getMarketType())) {
                    responses.add(StockPriceResponse.of(
                            StockResponse.from(stock),
                            provider.fetchPrice(StockRequest.from(stock))
                    ));
                    break;
                }
            }
        }
        return responses;
    }

    public StockPriceDetail fetchPriceDetail(String code) {
        Stock stock = stockService.getStock(code);
        return providers.stream()
                .filter(p -> p.supports(stock.getMarketType()))
                .findFirst()
                .map(p -> p.fetchPrice(StockRequest.from(stock)))
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 시장 타입입니다: " + stock.getMarketType()));
    }
}