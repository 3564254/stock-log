package com.stocklog.domain.stock.info;

import com.stocklog.domain.stock.common.type.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stock_info")
public class StockInfo {

    @Id
    @Column(name = "stock_code", length = 20)
    private String code;

    @Column(nullable = false)
    private String name;            // 상품명

    private String nameEng;         // 상품영문명

    private String sector;          // 산업 분류

    @Enumerated(EnumType.STRING)
    private Nation nation;          // 국가

    @Enumerated(EnumType.STRING)
    private MarketType marketType;  // 시장 구분

    @Enumerated(EnumType.STRING)
    private Currency currency;      // 거래 통화

    private BigDecimal totalShares;

    private BigDecimal parValue;

    @Enumerated(EnumType.STRING)
    private AssetType assetType;          // ETF 여부

    @Enumerated(EnumType.STRING)
    private TradingStatus tradingStatus;

    @Column(nullable = false)
    private BigDecimal leverage;       // 수익률 배수 (기본값 1)

    @Column(nullable = false)
    private boolean isPtp;          // PTP 종목 여부 (미국주식 세금 리스크)



    @Builder
    public StockInfo(String code, String name, String nameEng, String sector, Nation nation,
                     MarketType marketType, Currency currency, BigDecimal totalShares,
                     BigDecimal parValue, AssetType assetType, TradingStatus tradingStatus,
                     BigDecimal leverage, Boolean isPtp) {
        this.code = code;
        this.name = name;
        this.nameEng = nameEng;
        this.sector = sector;
        this.nation = nation;
        this.marketType = marketType;
        this.currency = currency;
        this.totalShares = totalShares;
        this.parValue = parValue;
        this.assetType = assetType;
        this.tradingStatus = tradingStatus;
        this.leverage = (leverage.equals(BigDecimal.ZERO)) ? BigDecimal.ONE : leverage;
        this.isPtp = isPtp != false;
    }
}