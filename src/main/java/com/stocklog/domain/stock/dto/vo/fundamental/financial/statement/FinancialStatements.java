package com.stocklog.domain.stock.dto.vo.fundamental.financial.statement;

import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail.BalanceSheet;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail.CashFlow;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail.IncomeStatement;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FinancialStatements {
    private BalanceSheet balanceSheet; // 재무상태표
    private CashFlow cashFlow; // 현금흐름표
    private IncomeStatement incomeStatement; // 손익계산서
}
