package com.stocklog.modules.stock.chart;

import com.stocklog.modules.stock.chart.id.StockChartId;
import com.stocklog.modules.common.type.Period;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(StockChartId.class)
@Table(name = "stock_chart",
        indexes = {@Index(name = "idx_chart_code_date", columnList = "stock_code, trade_date")})
public class StockChart {

    @Id
    @Column(name = "stock_code", length = 20)
    private String code;

    @Id
    @Column(name = "trade_date")
    private LocalDate tradeDate;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "period")
    private Period period;

    @Column(precision = 15, scale = 2)
    private BigDecimal openPrice;  // 시가

    @Column(precision = 15, scale = 2)
    private BigDecimal highPrice;  // 고가

    @Column(precision = 15, scale = 2)
    private BigDecimal lowPrice;   // 저가

    @Column(precision = 15, scale = 2)
    private BigDecimal closePrice; // 종가

    @Column(precision = 20, scale = 0)
    private BigDecimal volume; // 거래량


    @Builder
    public StockChart(String code, LocalDate tradeDate, BigDecimal openPrice, BigDecimal highPrice,
                      BigDecimal lowPrice, BigDecimal closePrice, BigDecimal volume, Period period) {
        this.code = code;
        this.tradeDate = tradeDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.period = period;
    }
}