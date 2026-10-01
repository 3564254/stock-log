package com.stocklog.modules.stock.stock;

import com.stocklog.modules.stock.domain.type.Currency;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.common.type.Nation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock", indexes = {
        @Index(name = "idx_stock_name", columnList = "name"),
        @Index(name = "idx_stock_standard_code", columnList = "standard_code")
})
public class Stock {

    @Id
    @Column(name = "stock_code", length = 20)
    private String code; // 종목 코드 (예: 005930, AAPL)

    @Column(name = "standard_code", length = 50)
    private String standardCode; // 표준 코드 (ISIN 등)

    @Column(nullable = false)
    private String name; // 종목명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketType market; // 상장 시장 타입

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Nation nation; // 거래 국가

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency; // 거래 통화

    private String sector; // 산업 섹터

    @Column(name = "realtime_code", length = 20)
    private String realtimeCode; // 실시간 시세 조회를 위한 매핑 코드

    @Builder
    public Stock(String code, String standardCode, String name, MarketType market,
                 Nation nation, Currency currency, String sector, String realtimeCode) {
        this.code = code;
        this.standardCode = standardCode;
        this.name = name;
        this.market = market;
        this.nation = nation;
        this.currency = currency;
        this.sector = sector;
        this.realtimeCode = realtimeCode;
    }
}
