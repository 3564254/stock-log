package com.stocklog.domain.stock.entity;


import com.stocklog.domain.stock.type.Currency;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.domain.stock.type.Nation;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {
    @Id
    @Column(name = "stock_code", length = 20)
    private String code;

    @Column(name = "standard_code", length = 20, nullable = false)
    private String standardCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "market_type")
    @Enumerated(EnumType.STRING)
    private MarketType marketType;

    @Enumerated(EnumType.STRING)
    private Nation nation;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    @Column(name = "realtime_code")
    private String realtimeCode;

    @Column(name = "base_price")
    private BigDecimal basePrice;

    @Column(name = "market_cap", precision = 25, scale = 4)
    private BigDecimal marketCap;

    private String sector;

    @Builder
    public Stock(String code, String standardCode, String name, MarketType marketType, Nation nation, Currency currency, String realtimeCode, BigDecimal basePrice, BigDecimal marketCap, String sector) {
        this.code = code;
        this.standardCode = standardCode;
        this.name = name;
        this.marketType = marketType;
        this.nation = nation;
        this.currency = currency;
        this.realtimeCode = realtimeCode;
        this.basePrice = basePrice;
        this.marketCap = marketCap;
        this.sector = sector;
    }

    public void updatePriceInfo(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }
}
