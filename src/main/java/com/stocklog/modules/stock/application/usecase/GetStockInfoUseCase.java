package com.stocklog.modules.stock.application.usecase;

import com.stocklog.modules.stock.info.StockInfo;
import com.stocklog.modules.stock.info.dto.StockInfoDto;

public interface GetStockInfoUseCase {
    StockInfo getStockInfo(String code);

    StockInfoDto getStockInfoDto(String code);
}
