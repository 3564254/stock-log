package com.stocklog.modules.purchase.trade.entity;

import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.stock.info.StockInfo;
import com.stocklog.global.common.base.BaseTimeEntity;
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