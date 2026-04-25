package com.stocklog.domain.stock.service;

import com.stocklog.global.common.StockCache;
import com.stocklog.domain.stock.dto.internal.StockLoadDto;
import com.stocklog.domain.stock.dto.response.StockResponse;
import com.stocklog.domain.stock.entity.Stock;
import com.stocklog.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StockService {
    private final StockRepository stockRepository;
    private final StockCache stockCache;

    public long getCount() {
        return stockRepository.count();
    }

    public StockResponse getStockResponse(String code) {
        return StockResponse.from(getStock(code));
    }

    @Transactional
    public void saveAll(List<StockLoadDto> dtos) {
        List<Stock> entities = dtos.stream()
                .map(StockLoadDto::toEntity)
                .toList();
        stockRepository.saveAll(entities);
    }

    public String getName(String code) {
        return stockCache.getName(code);
    }

    public Stock getReference(String code) {
        return stockRepository.getReferenceById(code);
    }

    public Stock getStock(String code) {
        return stockRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다: " + code));
    }

    @Transactional(readOnly = true)
    public Slice<Stock> searchStocks(String keyword, Pageable pageable) {
        return stockRepository.findByKeyword(keyword, pageable);
    }
}