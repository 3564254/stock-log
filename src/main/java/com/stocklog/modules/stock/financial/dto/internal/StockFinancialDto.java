package com.stocklog.modules.stock.financial.dto.internal;

import com.stocklog.modules.stock.financial.StockFinancial;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record StockFinancialDto(
        LocalDate baseDate,
        // 수익성 (TTM)
        BigDecimal grossMargin,
        BigDecimal operatingMargin,
        BigDecimal netMargin,
        BigDecimal roe,
        BigDecimal roa,

        // 성장성 (YoY)
        BigDecimal revenueGrowth,
        BigDecimal epsGrowth,

        // 배당
        BigDecimal dividendYield,
        BigDecimal payoutRatio,

        // 안정성 및 효율성
        BigDecimal currentRatio,
        BigDecimal debtToEquity,    // 부채비율
        BigDecimal inventoryTurnover,

        // 출처 · 기간. 없으면 null
        String source
) {
    /** 지표가 하나도 없으면 true. 이런 결과는 저장하지 않는다 */
    public boolean isEmpty() {
        return grossMargin == null && operatingMargin == null && netMargin == null && roe == null && roa == null
                && revenueGrowth == null && epsGrowth == null && dividendYield == null && payoutRatio == null
                && currentRatio == null && debtToEquity == null && inventoryTurnover == null;
    }


    /** 실패 · 데이터 없음 */
    public static StockFinancialDto createFallback() {
        return StockFinancialDto.builder().build();
    }

    public static StockFinancialDto from(StockFinancial stockFinancial) {
        return StockFinancialDto.builder()
                .baseDate(stockFinancial.getBaseDate())
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
                .source(stockFinancial.getSource())
                .build();

    }

    public static StockFinancial toEntity(StockFinancialDto financialDto, String code) {
        return StockFinancial.builder()
                .code(code)
                .baseDate(financialDto.baseDate())
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
                .source(financialDto.source())
                .build();
    }

}