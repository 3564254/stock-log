package com.stocklog.modules.stock.valutation;

import com.stocklog.modules.stock.domain.model.StockDateId;
import com.stocklog.global.common.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(StockDateId.class)
@Table(name = "stock_valuation",
        indexes = {@Index(name = "idx_valuation_date_code", columnList = "base_date, stock_code")})
public class StockValuation extends BaseTimeEntity {

    @Id
    @Column(name = "stock_code", length = 20)
    private String code;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate; // 기준일자 (예: 2026-04-23)

    @Column(precision = 10, scale = 2)
    private BigDecimal per;

    @Column(precision = 10, scale = 2)
    private BigDecimal pbr;

    @Column(precision = 15, scale = 2)
    private BigDecimal peg;

    @Column(precision = 15, scale = 2)
    private BigDecimal eps;

    @Column(precision = 15, scale = 2)
    private BigDecimal feps;

    @Column(precision = 15, scale = 2)
    private BigDecimal fper;

    private BigDecimal targetPrice;


    @Builder
    public StockValuation(String code, LocalDate baseDate, BigDecimal per, BigDecimal pbr, BigDecimal peg, BigDecimal eps, BigDecimal feps, BigDecimal fper, BigDecimal targetPrice) {
        this.code = code;
        this.baseDate = baseDate;
        this.per = per;
        this.pbr = pbr;
        this.peg = peg;
        this.eps = eps;
        this.feps = feps;
        this.fper = fper;
        this.targetPrice = targetPrice;
    }

    public void updateMetrics(BigDecimal per, BigDecimal pbr, BigDecimal peg, BigDecimal eps, BigDecimal feps, BigDecimal fper, BigDecimal targetPrice) {
        this.per = per;
        this.pbr = pbr;
        this.peg = peg;
        this.eps = eps;
        this.feps = feps;
        this.fper = fper;
        this.targetPrice = targetPrice;
    }
}