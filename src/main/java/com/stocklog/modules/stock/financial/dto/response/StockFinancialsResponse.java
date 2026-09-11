package com.stocklog.modules.stock.financial.dto.response;

import com.stocklog.modules.stock.financial.dto.internal.StockFinancialDto;
import lombok.Builder;

@Builder
public record StockFinancialsResponse(
        String symbol,
        StockFinancialDto stockFinancialDto
) {
    public static StockFinancialsResponse createFallback() {
        return StockFinancialsResponse.builder()
                .symbol("")
                .stockFinancialDto(StockFinancialDto.createFallback())
                .build();
    }
}