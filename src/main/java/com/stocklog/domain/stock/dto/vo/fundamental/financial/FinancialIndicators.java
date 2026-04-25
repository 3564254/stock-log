package com.stocklog.domain.stock.dto.vo.fundamental.financial;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class FinancialIndicators {
    private BigDecimal eps;             // 주당순이익
    private BigDecimal roe;             // ROE (%)
    private BigDecimal debtRatio;       // 부채비율 (%)
    private BigDecimal currentRatio;    // 유동비율 (%)
    private BigDecimal revenueGrowth;   // 매출성장률 (%)

    private BigDecimal operatingMargin; // 영업이익률 (%)

    private BigDecimal psr; // 주가매출비율
    private BigDecimal pbr; // 주가순자산비율 (PBR = 현재가 / BPS)
    private BigDecimal per; // 주가수익비율 (PER = 현재가 / EPS)
    private BigDecimal bps; // 주당순자산


    public static FinancialIndicators createFallback() {
        return FinancialIndicators.builder()
                .eps(null)
                .roe(null)
                .debtRatio(null)
                .currentRatio(null)
                .revenueGrowth(null)
                .psr(null)
                .pbr(null)
                .per(null)
                .bps(null)
                .build();
    }

    public boolean isEmpty() {
        return roe == null && debtRatio == null && currentRatio == null && revenueGrowth == null;
    }
}