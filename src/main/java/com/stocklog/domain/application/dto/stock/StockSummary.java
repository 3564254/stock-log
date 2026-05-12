package com.stocklog.domain.application.dto.stock;

import com.stocklog.domain.stock.info.dto.internal.StockInfoDto;
import com.stocklog.domain.stock.price.dto.internal.StockPriceDto;
import lombok.Builder;

@Builder
public record StockSummary(
        StockInfoDto info,
        StockPriceDto price
) {

}
