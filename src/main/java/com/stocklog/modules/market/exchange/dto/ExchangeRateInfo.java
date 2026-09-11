package com.stocklog.modules.market.exchange.dto;


import com.stocklog.modules.market.exchange.entity.ExchangeRate;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ExchangeRateInfo(
        String baseCurrency,   // 예: "USD", "JPY"
        String quoteCurrency,
        BigDecimal baseRate,     // 매매기준율 (API에서 가져온 conversion_rates 값)
        BigDecimal minRate,
        BigDecimal basisPoint,
        String rateChangeType,
        LocalDateTime validFrom,
        LocalDateTime validUntil

) {

    public static ExchangeRateInfo createFallback() {
        return ExchangeRateInfo.builder()
                .baseCurrency("UNKNOWN")
                .baseRate(BigDecimal.ZERO)
                .build();
    }

    public static ExchangeRateInfo from(ExchangeRate exchangeRate) {
        return ExchangeRateInfo.builder()
                .baseCurrency(exchangeRate.getTargetCurrency().name())
                .baseRate(exchangeRate.getRate())
                .build();
    }

}