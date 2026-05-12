package com.stocklog.global.common;

import com.stocklog.domain.stock.common.StockMasterDto;
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
    private final Map<String, StockMasterDto> stockNameCache = new ConcurrentHashMap<>();


    public void loadStockNamesToCache(List<StockMasterDto> results) {
        stockNameCache.clear();

        for (StockMasterDto stock : results) {
            if (stock.shortCode() != null) {
                stockNameCache.put(stock.shortCode().trim(), stock);
            }
        }
        log.info("주식 이름 캐시 로드 완료: {}건", stockNameCache.size());
    }

    public StockMasterDto getStockLoadDto(String code) {
        return stockNameCache.get(code);
    }
}
