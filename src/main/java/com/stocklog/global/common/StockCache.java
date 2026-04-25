package com.stocklog.global.common;

import com.stocklog.domain.stock.entity.Stock;
import com.stocklog.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class StockCache {
    private final StockRepository stockRepository;
    private final Map<String, String> stockNameCache = new ConcurrentHashMap<>();


    public void loadStockNamesToCache() {
        List<Stock> allStocks = stockRepository.findAll();
        stockNameCache.clear();


        for (Stock stock : allStocks) {
            if (stock.getCode() != null) {
                stockNameCache.put(stock.getCode().trim(), stock.getName().trim());
            }
        }
        log.info("주식 이름 캐시 로드 완료: {}건", stockNameCache.size());
    }

    public String getName(String code) {
        return stockNameCache.get(code);
    }
}
