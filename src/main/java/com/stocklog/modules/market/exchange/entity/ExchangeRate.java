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

/**
 * 메인 환율 (단일 행, id = 1).
 * 방향: 1 baseCurrency = rate quoteCurrency  (예: 1 USD = 1,385.20 KRW)
 */
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

    /** 외부 API에서 받아온 시각 (갱신 판단용) */
    private LocalDateTime fetchedAt;

    public static ExchangeRate from(ExchangeRateInfo info, LocalDateTime now) {
        ExchangeRate e = new ExchangeRate();
        e.update(info, now);
        return e;
    }

    /** 같은 행을 덮어쓴다 (영속 상태면 더티 체킹으로 UPDATE) */
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

    /** 유효기간이 있으면 그 기준, 없으면 받아온 지 DEFAULT_TTL 이내인지로 판단 */
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
