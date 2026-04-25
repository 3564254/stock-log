package com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomeStatement {
    private BigDecimal grossProfit;              // 매출총이익 (기존)
    private BigDecimal netIncomeLoss;            // 당기순이익 (기존)
    private BigDecimal operatingExpenses;        // 영업비용 (기존)
    private BigDecimal revenue;                  // 매출액 - 모든 성장의 근원, PSR 계산용
    private BigDecimal operatingIncome;          // 영업이익 - 본업의 경쟁력, 영업이익률 계산용
    private BigDecimal epsBasic;               // 기본 주당순이익 (소수점 필수!)
    private BigDecimal epsDiluted;             // 희석 주당순이익 (PER 계산의 핵심, 소수점 필수!)
}
