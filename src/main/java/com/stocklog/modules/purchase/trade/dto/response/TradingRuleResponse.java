package com.stocklog.modules.purchase.trade.dto.response;

import com.stocklog.modules.purchase.trade.dto.vo.TradingRuleDto;
import com.stocklog.modules.purchase.trade.entity.TradingRule;
import lombok.Builder;

import java.util.List;

@Builder
public record TradingRuleResponse(
        Long memberId,
        List<TradingRuleDto> ruleList
) {
    public static TradingRuleResponse of(Long memberId, List<TradingRule> rules) {
        return TradingRuleResponse.builder()
                .memberId(memberId)
                .ruleList(rules.stream()
                        .map(TradingRuleDto::from).toList())
                .build();
    }
}
