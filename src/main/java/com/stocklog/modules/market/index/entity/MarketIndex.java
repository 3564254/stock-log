package com.stocklog.modules.market.index.entity;
import com.stocklog.modules.market.index.type.IndexType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "market_index")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketIndex {

    @Id
    @Column(name = "index_symbol", length = 20)
    private String symbol; // 예: "0001"(KOSPI), "^GSPC"(S&P500), "GOLD"

    @Column(nullable = false)
    private String name; // 지수/자산 명칭 (예: "S&P 500")

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IndexType indexType; // KOSPI, SP500, COMMODITY, VOLATILITY

    @Column(precision = 19, scale = 4)
    private BigDecimal currentValue; // 현재 수치

    @Column(precision = 19, scale = 4)
    private BigDecimal changeValue; // 전일 대비 등락

    @Column(precision = 10, scale = 2)
    private BigDecimal changeRate; // 등락률

    private LocalDate updatedAt;


    public void update(BigDecimal value, BigDecimal changeValue, BigDecimal changeRate) {
        this.currentValue = value;
        this.changeValue = changeValue;
        this.changeRate = changeRate;
        this.updatedAt = LocalDate.now();
    }

    @Builder

    public MarketIndex(String symbol, String name, IndexType indexType, BigDecimal currentValue, BigDecimal changeValue, BigDecimal changeRate, LocalDate updatedAt) {
        this.symbol = symbol;
        this.name = name;
        this.indexType = indexType;
        this.currentValue = currentValue;
        this.changeValue = changeValue;
        this.changeRate = changeRate;
        this.updatedAt = LocalDate.now();
    }
}
