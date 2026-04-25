package com.stocklog.domain.stock.dto.vo.fundamental.financial;

import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.FinancialStatements;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FinancialPeriodData {
    private int year;
    private int quarter;
    private String form; // 10-K, 10-Q
    private String startDate;
    private String endDate;

    private BigDecimal revenueGrowth;         // 매출액 증감률
    private BigDecimal operatingIncomeGrowth; // 영업이익 증감률
    private BigDecimal netIncomeGrowth;       // 당기순이익 증감률

    private FinancialStatements report;
}
