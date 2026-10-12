package com.stocklog.modules.market.exchange.entity;

import com.stocklog.global.common.base.BaseTimeEntity;
import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.stock.domain.type.Currency;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate extends BaseTimeEntity {

    public static final Long MAIN_ID = 1L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    @Id
    private Long id = MAIN_ID;

    @Enumerated(EnumType.STRING)
    private Currency baseCurrency;      // USD

    @Enumerated(EnumType.STRING)
    private Currency quoteCurrency;     // KRW

    @Column(precision = 15, scale = 4)
    private BigDecimal rate;            // 매매기준율

    @Column(precision = 15, scale = 4)
    private BigDecimal midRate;

    @Column(precision = 15, scale = 4)
    private BigDecimal basisPoint;

    private String rateChangeType;

    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    private LocalDateTime fetchedAt;

    public static ExchangeRate from(ExchangeRateInfo info, LocalDateTime now) {
        ExchangeRate e = new ExchangeRate();
        e.update(info, now);
        return e;
    }

    public void update(ExchangeRateInfo info, LocalDateTime now) {
        this.baseCurrency = parseOr(info.baseCurrency(), Currency.USD);
        this.quoteCurrency = parseOr(info.quoteCurrency(), Currency.KRW);
        this.rate = info.baseRate();
        this.midRate = info.midRate();
        this.basisPoint = info.basisPoint();
        this.rateChangeType = info.rateChangeType();
        this.validFrom = info.validFrom();
        this.validUntil = info.validUntil();
        this.fetchedAt = now;
    }

    public boolean hasValidRate() {
        return rate != null && rate.signum() > 0;
    }

    public boolean isFresh(LocalDateTime now) {
        if (!hasValidRate()) return false;
        if (validUntil != null) return now.isBefore(validUntil);
        return fetchedAt != null && now.isBefore(fetchedAt.plus(DEFAULT_TTL));
    }

    private static Currency parseOr(String code, Currency fallback) {
        if (code == null || code.isBlank()) return fallback;
        Currency c = Currency.fromString(code);
        return (c == null || c == Currency.UNKNOWN) ? fallback : c;
    }
}
