package com.stocklog.domain.marketdata.exchange.dto;


import com.stocklog.domain.marketdata.exchange.entity.ExchangeRate;
import lombok.Builder;
import java.math.BigDecimal;

@Builder
public record ExchangeRateInfo(
        String currencyCode,    // 예: "USD", "JPY"
        BigDecimal baseRate     // 매매기준율 (API에서 가져온 conversion_rates 값)
) {

    public static ExchangeRateInfo createFallback() {
        return ExchangeRateInfo.builder()
                .currencyCode("UNKNOWN")
                .baseRate(BigDecimal.ZERO)
                .build();
    }

    public static ExchangeRateInfo from(ExchangeRate exchangeRate) {
        return ExchangeRateInfo.builder()
                .currencyCode(exchangeRate.getTargetCurrency().name())
                .baseRate(exchangeRate.getRate())
                .build();
    }
}