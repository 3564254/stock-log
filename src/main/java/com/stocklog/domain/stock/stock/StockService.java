package com.stocklog.domain.stock.stock;


import com.stocklog.domain.stock.common.StockMasterDto;
import com.stocklog.domain.stock.stock.dto.StockDto;
import com.stocklog.global.common.StockCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockCache cache;

    public long getCount() {
        return stockRepository.count();
    }

    public StockDto getStock(String code) {
        StockMasterDto stockMasterDto = cache.getStockLoadDto(code);
        if (stockMasterDto != null) {
            return StockDto.from(stockMasterDto);
        }
        log.info("캐시에 존재하지 않는 종목입니다. [{}]", code);
        Stock stock = stockRepository.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다: " + code));
        return StockDto.from(stock);
    }

    public List<StockDto> getStockByKeword(String keyword, Pageable pageable) {
        List<StockDto> result = new ArrayList<>();
        Slice<Stock> stocks = stockRepository.findByKeyword(keyword, pageable);
        for (Stock stock : stocks) {
            result.add(StockDto.from(stock));
        }
        return result;
    }

    public void saveAll(List<StockMasterDto> results) {
        List<Stock> stocks = new ArrayList<>();
        for (StockMasterDto masterDto : results) {
            stocks.add(StockMasterDto.toEntity(masterDto));
        }
        stockRepository.saveAll(stocks);
    }




}
