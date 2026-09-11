package com.stocklog.modules.market.exchange.entity;

import com.stocklog.modules.stock.domain.type.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "exchange_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate {

    @Id
    private Long id = 1L; // 항상 1로 고정 (단일 행 보장)

    @Enumerated(EnumType.STRING)
    private Currency baseCurrency;

    @Enumerated(EnumType.STRING)
    private Currency targetCurrency;

    private BigDecimal rate;


    // 정적 팩토리 메서드
    public static ExchangeRate of(Currency base, Currency target, BigDecimal rate) {
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.baseCurrency = base;
        exchangeRate.targetCurrency = target;
        exchangeRate.rate = rate;
        return exchangeRate;
    }

    public static ExchangeRate of(BigDecimal rate) {
        ExchangeRate exchangeRate = new ExchangeRate();
        exchangeRate.baseCurrency = Currency.KRW;
        exchangeRate.targetCurrency = Currency.USD;
        exchangeRate.rate = rate;
        return exchangeRate;
    }
}