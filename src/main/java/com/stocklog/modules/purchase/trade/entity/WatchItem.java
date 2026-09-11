package com.stocklog.modules.purchase.trade.entity;

import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.stock.analysis.domain.signal.vo.MarketPhase;
import com.stocklog.modules.stock.info.StockInfo;
import com.stocklog.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "watch_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_watch_item_stock", columnNames = {"member_id", "stock_code"})
})
public class WatchItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회원 정보 연관관계 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_phase")
    private MarketPhase marketPhase;

    // 💡 딱 하나만 남긴 관찰 목표가
    @Column(name = "target_price")
    private Double targetPrice;

    @Builder
    public WatchItem(Member member, StockInfo stock, MarketPhase marketPhase) {
        this.member = member;
        this.stock = stock;
        this.marketPhase = marketPhase;
    }

    // 국면과 목표가를 함께 갱신하는 메서드
    public void updateAnalyzedSignal(MarketPhase marketPhase, Double targetPrice) {
        this.marketPhase = marketPhase;
        this.targetPrice = targetPrice;
    }


}