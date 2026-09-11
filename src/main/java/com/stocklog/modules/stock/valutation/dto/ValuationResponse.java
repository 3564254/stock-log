package com.stocklog.modules.stock.valutation.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValuationResponse {
    private BigDecimal peg;
    private BigDecimal eps;
    private BigDecimal feps;
    private BigDecimal fper;
    private BigDecimal targetPrice;

    public static ValuationResponse createFallback() {
        return ValuationResponse.builder()
                .targetPrice(BigDecimal.ZERO)
                .eps(BigDecimal.ZERO)
                .feps(BigDecimal.ZERO)
                .peg(BigDecimal.ZERO)
                .fper(BigDecimal.ZERO)
                .build();
    }
}
