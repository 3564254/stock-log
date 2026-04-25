package com.stocklog.domain.marketdata.index.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "market_index_history",
        // ✨ 복합 유니크 인덱스 설정 (동일 symbol, 동일 date 중복 방지)
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_symbol_date", columnNames = {"symbol", "date"})
        },
        indexes = {
                @Index(name = "idx_symbol_date", columnList = "symbol, date")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MarketIndexHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol; // MarketIndex의 symbol과 매핑

    @Column(nullable = false)
    private LocalDate date; // 데이터 기록 날짜

    @Column(precision = 19, scale = 4)
    private BigDecimal closeValue; // 당일 종가 (또는 기준 수치)

    @Column(precision = 19, scale = 4)
    private BigDecimal highValue; // 당일 고점 (필요 시)

    @Column(precision = 19, scale = 4)
    private BigDecimal lowValue;  // 당일 저점 (필요 시)

    @Column(precision = 19, scale = 4)
    private BigDecimal volume;    // 거래량 (필요 시)

    @Builder
    public MarketIndexHistory(String symbol, LocalDate date, BigDecimal closeValue, BigDecimal highValue, BigDecimal lowValue, BigDecimal volume) {
        this.symbol = symbol;
        this.date = date;
        this.closeValue = closeValue;
        this.highValue = highValue;
        this.lowValue = lowValue;
        this.volume = volume;
    }
}