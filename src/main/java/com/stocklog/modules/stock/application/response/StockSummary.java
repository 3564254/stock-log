package com.stocklog.modules.stock.application.response;

import com.stocklog.modules.stock.info.dto.StockInfoDto;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import lombok.Builder;

@Builder
public record StockSummary(
        StockInfoDto info,
        StockPriceDto price
) {

}
