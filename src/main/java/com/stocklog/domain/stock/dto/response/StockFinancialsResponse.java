package com.stocklog.domain.stock.dto.response;

import com.stocklog.domain.stock.dto.vo.fundamental.financial.FinancialIndicators;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.FinancialPeriodData;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record StockFinancialsResponse(
        String code,
        List<FinancialPeriodData> data,
        FinancialIndicators indicators,
        BigDecimal maxRevenue,
        BigDecimal maxOperatingIncome,
        BigDecimal maxNetIncome
) {
    public static StockFinancialsResponse createFallback() {
        return StockFinancialsResponse.builder()
                .code("")
                .data(List.of())
                .indicators(FinancialIndicators.createFallback())
                .build();
    }
}