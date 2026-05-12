package com.stocklog.domain.stock.financial.dto.internal;

import com.stocklog.domain.stock.financial.StockFinancial;
import lombok.Builder;
import com.stocklog.global.util.FinancialConst;

import java.math.BigDecimal;

@Builder
public record StockFinancialDto(
        // --- 수익성 (TTM) ---
        BigDecimal grossMargin,
        BigDecimal operatingMargin,
        BigDecimal netMargin,
        BigDecimal roe,
        BigDecimal roa,

        // --- 성장성 (YoY) ---
        BigDecimal revenueGrowth,
        BigDecimal epsGrowth,

        // --- 배당 ---
        BigDecimal dividendYield,
        BigDecimal payoutRatio,

        // --- 안정성 및 효율성 ---
        BigDecimal currentRatio,
        BigDecimal debtToEquity,    // 부채비율
        BigDecimal inventoryTurnover
) {

    public static StockFinancialDto createFallback() {
        return StockFinancialDto.builder()
                .grossMargin(BigDecimal.ZERO)
                .operatingMargin(BigDecimal.ZERO)
                .netMargin(BigDecimal.ZERO)
                .roe(BigDecimal.ZERO)
                .roa(BigDecimal.ZERO)
                .revenueGrowth(BigDecimal.ZERO)
                .epsGrowth(BigDecimal.ZERO)
                .dividendYield(BigDecimal.ZERO)
                .payoutRatio(BigDecimal.ZERO)
                .currentRatio(BigDecimal.ZERO)
                .debtToEquity(BigDecimal.ZERO)
                .inventoryTurnover(BigDecimal.ZERO)
                .build();
    }

    public static StockFinancialDto from(StockFinancial stockFinancial) {
        return StockFinancialDto.builder()
                .grossMargin(stockFinancial.getGrossMargin())
                .operatingMargin(stockFinancial.getOperatingMargin())
                .netMargin(stockFinancial.getNetMargin())
                .roe(stockFinancial.getRoe())
                .roa(stockFinancial.getRoa())
                .revenueGrowth(stockFinancial.getRevenueGrowth())
                .epsGrowth(stockFinancial.getEpsGrowth())
                .dividendYield(stockFinancial.getDividendYield())
                .payoutRatio(stockFinancial.getPayoutRatio())
                .currentRatio(stockFinancial.getCurrentRatio())
                .debtToEquity(stockFinancial.getDebtToEquity())
                .inventoryTurnover(stockFinancial.getInventoryTurnover())
                .build();

    }

    public static StockFinancial toEntity(StockFinancialDto financialDto, String code) {
        return StockFinancial.builder()
                .code(code)
                .grossMargin(financialDto.grossMargin())
                .operatingMargin(financialDto.operatingMargin())
                .netMargin(financialDto.netMargin())
                .roe(financialDto.roe())
                .roa(financialDto.roa())

                .revenueGrowth(financialDto.revenueGrowth())
                .epsGrowth(financialDto.epsGrowth())

                .dividendYield(financialDto.dividendYield())
                .payoutRatio(financialDto.payoutRatio())

                .currentRatio(financialDto.currentRatio())
                .debtToEquity(financialDto.debtToEquity())
                .inventoryTurnover(financialDto.inventoryTurnover())
                .build();
    }
}