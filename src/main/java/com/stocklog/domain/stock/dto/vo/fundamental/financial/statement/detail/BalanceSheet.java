package com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail;
import lombok.*;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BalanceSheet {
    private BigDecimal assets;                   // 총자산 (기존)
    private BigDecimal liabilities;              // 총부채 (기존)
    private BigDecimal stockholdersEquity;       // 자본총계(주주지분) - ROE, PBR 계산의 핵심
    private BigDecimal currentAssets;            // 유동자산 (1년 내 현금화 가능 자산)
    private BigDecimal currentLiabilities;       // 유동부채 (1년 내 갚아야 할 빚)
}
