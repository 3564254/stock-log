package com.stocklog.modules.stock.application.service;

import com.stocklog.modules.stock.application.usecase.GetStockInfoUseCase;
import com.stocklog.modules.stock.info.StockInfo;
import com.stocklog.modules.stock.info.StockInfoService;
import com.stocklog.modules.stock.info.dto.StockInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetStockInfoService implements GetStockInfoUseCase {

    private final StockInfoService infoService;

    @Override
    public StockInfo getStockInfo(String code) {
        return infoService.fetchInfo(code);
    }

    @Override
    public StockInfoDto getStockInfoDto(String code) {
        return infoService.fetchInfoDto(code);
    }
}
