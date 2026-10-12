package com.stocklog.modules.market.exchange.dto;


import com.stocklog.modules.market.exchange.entity.ExchangeRate;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 1 baseCurrency = baseRate quoteCurrency  (예: USD / KRW = 1,385.20)
 * 공급자에 따라 baseRate 외의 필드는 비어 있을 수 있다.
 */
@Builder
public record ExchangeRateInfo(
        String baseCurrency,     // 예: "USD"
        String quoteCurrency,    // 예: "KRW"
        BigDecimal baseRate,     // 매매기준율
        BigDecimal midRate,
        BigDecimal basisPoint,
        String rateChangeType,
        LocalDateTime validFrom,
        LocalDateTime validUntil
) {

    /** 모든 공급자가 실패하고 DB 값도 없을 때. baseRate = 0 이면 화면은 '불러오지 못했어요'로 표시한다 */
    public static ExchangeRateInfo createFallback() {
        return ExchangeRateInfo.builder()
                .baseCurrency("USD")
                .quoteCurrency("KRW")
                .baseRate(BigDecimal.ZERO)
                .build();
    }

    public static ExchangeRateInfo from(ExchangeRate e) {
        return ExchangeRateInfo.builder()
                .baseCurrency(e.getBaseCurrency() != null ? e.getBaseCurrency().name() : "USD")
                .quoteCurrency(e.getQuoteCurrency() != null ? e.getQuoteCurrency().name() : "KRW")
                .baseRate(e.getRate())
                .midRate(e.getMidRate())
                .basisPoint(e.getBasisPoint())
                .rateChangeType(e.getRateChangeType())
                .validFrom(e.getValidFrom())
                .validUntil(e.getValidUntil())
                .build();
    }

    public boolean hasValidRate() {
        return baseRate != null && baseRate.signum() > 0;
    }
}
