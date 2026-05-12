package com.stocklog.domain.tradelog.entity;

import com.stocklog.domain.member.entity.Member;
import com.stocklog.domain.stock.info.StockInfo;
import com.stocklog.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeLog extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stock;

    private LocalDate tradeDate;
    private BigDecimal quantity;

    private BigDecimal profit;

    @Builder
    public TradeLog(Member member, StockInfo stock, LocalDate tradeDate,
                    BigDecimal quantity, BigDecimal profit) {
        this.member = member;
        this.stock = stock;
        this.tradeDate = tradeDate;
        this.quantity = quantity;
        this.profit = profit;
    }
}