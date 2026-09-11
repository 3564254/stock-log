package com.stocklog.modules.purchase.trade.dto.vo;

import com.stocklog.modules.purchase.trade.entity.TradingRule;
import com.stocklog.modules.purchase.trade.type.IndicatorType;
import com.stocklog.modules.purchase.trade.type.Operator;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record TradingRuleDto(
        Long id,
        String title,
        IndicatorType indicatorType,
        Operator operator,
        Double targetValue,
        boolean isActive,
        LocalDateTime createdAt
) {
    public static TradingRuleDto from(TradingRule tradingRule) {
        return TradingRuleDto.builder()
                .id(tradingRule.getId())
                .title(tradingRule.getTitle())
                .indicatorType(tradingRule.getIndicatorType())
                .operator(tradingRule.getOperator())
                .targetValue(tradingRule.getTargetValue())
                .isActive(tradingRule.isActive())
                .createdAt(tradingRule.getCreatedDate())
                .build();
    }
}
