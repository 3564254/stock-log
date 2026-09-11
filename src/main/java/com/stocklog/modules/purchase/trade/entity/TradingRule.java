package com.stocklog.modules.purchase.trade.entity;

import com.stocklog.modules.purchase.trade.type.IndicatorType;
import com.stocklog.modules.purchase.trade.type.Operator;
import com.stocklog.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trading_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "title", nullable = false, length = 100)
    private String title; // 예: "RSI 과매수 권 진입 시 매수 금지"

    @Enumerated(EnumType.STRING)
    @Column(name = "indicator_type", nullable = false, length = 30)
    private IndicatorType indicatorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", length = 20)
    private Operator operator;

    @Column(name = "target_value")
    private Double targetValue; // 기준 지표 값 (예: 30.0 또는 70.0)

    @Column(name = "is_active", nullable = false)
    private boolean isActive; // 규칙 활성화 여부


    @Builder
    public TradingRule(Long memberId, String title, IndicatorType indicatorType, Operator operator, Double targetValue, boolean isActive) {
        this.memberId = memberId;
        this.title = title;
        this.indicatorType = indicatorType;
        this.operator = operator;
        this.targetValue = targetValue;
        this.isActive = isActive;
    }

    // ================= 비즈니스 로직 (수정 업데이트용) =================

    /**
     * 매매 규칙 내용 수정 (JPA 더티 체킹)
     */
    public void updateRule(String title, IndicatorType indicatorType, Operator operator, Double targetValue) {
        this.title = title;
        this.indicatorType = indicatorType;
        this.operator = operator;
        this.targetValue = targetValue;
    }


    /**
     * 매매 규칙 활성화/비활성화 토글
     */
    public void toggleActive(boolean isActive) {
        this.isActive = isActive;
    }
}