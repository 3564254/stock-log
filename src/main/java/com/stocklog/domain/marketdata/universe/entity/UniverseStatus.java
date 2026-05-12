package com.stocklog.domain.marketdata.universe.entity;

import com.stocklog.domain.stock.analysis.domain.signal.vo.EntrySignal;
import com.stocklog.domain.stock.analysis.domain.signal.vo.MarketPhase;
import com.stocklog.domain.marketdata.universe.dto.UniverseDto;
import com.stocklog.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UniverseStatus extends BaseTimeEntity {

    @Id
    @Column(unique = true)
    private String code;         // 티커 (XLK, SOXX 등)

    @Enumerated(EnumType.STRING)
    private MarketPhase phase;   // 현재 국면 (ENTRY_READY 등)

    private BigDecimal currentPrice; // 현재가
    private BigDecimal changeRate;   // 등락률
    private BigDecimal entryPrice;   // 1차 타점

    @Builder
    public UniverseStatus(String code, MarketPhase phase, BigDecimal currentPrice, BigDecimal changeRate, BigDecimal entryPrice) {
        this.code = code;
        this.phase = phase;
        this.currentPrice = currentPrice;
        this.changeRate = changeRate;
        this.entryPrice = entryPrice;
    }

    // 비즈니스 로직: 업데이트용 메서드
    public void updateStatus(EntrySignal signal, BigDecimal price, BigDecimal changeRate) {
        this.phase = signal.phase(); // Analyzer에서 도출된 Enum
        this.currentPrice = price;
        this.changeRate = changeRate;
        this.entryPrice = new BigDecimal(signal.primaryTarget());
    }

    public void updateStatus(UniverseDto status) {
        this.phase = status.phase(); // Analyzer에서 도출된 Enum
        this.currentPrice = status.currentPrice();
        this.changeRate = status.changeRate();
        this.entryPrice = status.entryPrice();
    }

    public void updateStatus(MarketPhase phase, BigDecimal currentPrice, BigDecimal changeRate, BigDecimal entryPrice) {
        this.phase = phase;
        this.currentPrice = currentPrice;
        this.changeRate = changeRate;
        this.entryPrice = entryPrice;
    }
}
