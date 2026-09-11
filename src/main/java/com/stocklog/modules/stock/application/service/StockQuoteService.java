package com.stocklog.modules.stock.application.service;

import com.stocklog.modules.stock.application.response.StockQuote;
import com.stocklog.modules.stock.application.usecase.StockQuoteUseCase;
import com.stocklog.modules.stock.price.StockPriceService;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.stock.StockService;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockQuoteService implements StockQuoteUseCase {

    private final StockPriceService priceService;
    private final StockService stockService;

    @Override
    public StockQuote getStockQuote(String code) {
        StockPriceDto stockPriceDto = priceService.fetchPrice(code);
        StockDto stockDto = stockService.getStockDto(code);

        return StockQuote.of(stockDto, stockPriceDto);
    }

    @Override
    public List<StockQuote> getStockQuoteListByKeyword(String keyword, Pageable pageable) {
        List<StockDto> stockDtoList = stockService.getStockByKeword(keyword, pageable);
        if (stockDtoList.isEmpty()) {
            return List.of();
        }

        List<String> codes = stockDtoList.stream()
                .map(StockDto::code)
                .toList();

        Map<String, StockPriceDto> dtoMap = priceService.fetchPricesInBulk(codes);

        return stockDtoList.stream()
                .map(stock -> StockQuote.of(stock, dtoMap.get(stock.code())))
                .toList();
    }
}
