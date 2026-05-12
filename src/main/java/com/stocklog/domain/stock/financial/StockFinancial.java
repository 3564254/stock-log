package com.stocklog.domain.stock.financial;

import com.stocklog.domain.stock.info.StockInfo;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_financial")
public class StockFinancial {

    @Id
    @Column(name = "stock_code", length = 20)
    private String code;

    // --- 수익성 (JSON의 grossMarginTTM, netProfitMarginTTM 등) ---
    @Column(precision = 10, scale = 4)
    private BigDecimal grossMargin;      // 매출총이익률 (TTM)
    @Column(precision = 10, scale = 4)
    private BigDecimal operatingMargin;  // 영업이익률 (TTM)
    @Column(precision = 10, scale = 4)
    private BigDecimal netMargin;       // 순이익률 (TTM)
    @Column(precision = 10, scale = 4)
    private BigDecimal roe;             // ROE (TTM)
    @Column(precision = 10, scale = 4)
    private BigDecimal roa;             // ROA (TTM)

    // --- 성장성 (JSON의 revenueGrowthTTMYoy, epsGrowthTTMYoy 등) ---
    @Column(precision = 10, scale = 4)
    private BigDecimal revenueGrowth;   // 매출 성장률 (YoY)
    @Column(precision = 10, scale = 4)
    private BigDecimal epsGrowth;       // EPS 성장률 (YoY)

    // --- 안정성 및 효율성 (JSON의 currentRatioQuarterly, inventoryTurnoverTTM 등) ---
    @Column(precision = 10, scale = 4)
    private BigDecimal debtToEquity;    // 부채비율 (Quarterly)
    @Column(precision = 10, scale = 4)
    private BigDecimal currentRatio;    // 유동비율 (Quarterly)
    @Column(precision = 10, scale = 4)
    private BigDecimal inventoryTurnover; // 재고회전율 (TTM)

    // --- 배당 ---
    @Column(precision = 10, scale = 4)
    private BigDecimal dividendYield;   // 배당수익률 (TTM)
    @Column(precision = 10, scale = 4)
    private BigDecimal payoutRatio;     // 배당성향 (TTM)


    @Builder
    public StockFinancial(String code, BigDecimal grossMargin, BigDecimal operatingMargin,
                          BigDecimal netMargin, BigDecimal roe, BigDecimal roa,
                          BigDecimal revenueGrowth, BigDecimal epsGrowth,
                          BigDecimal debtToEquity, BigDecimal currentRatio,
                          BigDecimal inventoryTurnover, BigDecimal dividendYield,
                          BigDecimal payoutRatio) {
        this.code = code;
        this.grossMargin = grossMargin;
        this.operatingMargin = operatingMargin;
        this.netMargin = netMargin;
        this.roe = roe;
        this.roa = roa;
        this.revenueGrowth = revenueGrowth;
        this.epsGrowth = epsGrowth;
        this.debtToEquity = debtToEquity;
        this.currentRatio = currentRatio;
        this.inventoryTurnover = inventoryTurnover;
        this.dividendYield = dividendYield;
        this.payoutRatio = payoutRatio;
    }
}