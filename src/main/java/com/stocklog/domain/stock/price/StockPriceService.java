package com.stocklog.domain.stock.price;

import com.stocklog.domain.stock.common.StockMasterDto;
import com.stocklog.domain.stock.common.StockRequest;
import com.stocklog.domain.stock.price.dto.internal.StockPriceDto;
import com.stocklog.domain.stock.price.dto.response.StockPriceResponse;
import com.stocklog.domain.stock.stock.StockService;
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
@RequiredArgsConstructor
@Slf4j
public class StockPriceService {

    private final List<StockPriceProvider> providers;
    private final StockService stockService;


    public StockPriceDto fetchPrice(String code) {
        StockDto stock = stockService.getStock(code);
        StockPriceResponse response = providers.stream()
                .filter(providers -> providers.supports(stock.market()))
                .findFirst()
                .map(provider -> provider.fetchPrice(StockRequest.from(stock)))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다: " + code));
        return StockPriceDto.from(response);
    }


}