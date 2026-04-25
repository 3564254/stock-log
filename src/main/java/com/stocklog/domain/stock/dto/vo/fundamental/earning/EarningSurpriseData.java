package com.stocklog.domain.stock.dto.vo.fundamental.earning;

import lombok.*;

import java.math.BigDecimal;

@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class EarningSurpriseData {
    private BigDecimal actual;          // 실제 발표된 EPS
    private BigDecimal estimate;        // 시장 예상 EPS (컨센서스)
    private String period;              // 보고 기간 (예: 2023-03-31)
    private int quarter;                // 분기 (1, 2, 3, 4)
    private BigDecimal surprise;        // 실제 EPS - 예상 EPS 차이
    private BigDecimal surprisePercent; // 서프라이즈 비율 (%)
    private String symbol;              // 종목 티커 (예: AAPL)
    private int year;                   // 회계 연도
}
