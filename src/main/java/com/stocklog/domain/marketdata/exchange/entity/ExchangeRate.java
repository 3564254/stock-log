package com.stocklog.domain.marketdata.exchange.entity;

import com.stocklog.domain.stock.type.Currency;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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