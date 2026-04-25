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
public class CashFlow {
    private BigDecimal netIncomeLoss;            // 당기순이익 (기존)
    private BigDecimal operatingCashFlow;        // 영업활동 현금흐름 - 흑자부도 판별용 (진짜 들어온 돈)
    private BigDecimal capex;                    // 자본적 지출(공장/설비 투자) - 잉여현금흐름(FCF) 계산용
}
