package com.stocklog.domain.stock.financial.dto.response;

import com.stocklog.domain.stock.financial.dto.internal.StockFinancialDto;
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