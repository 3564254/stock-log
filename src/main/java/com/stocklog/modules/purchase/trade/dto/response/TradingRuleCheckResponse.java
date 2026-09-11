package com.stocklog.modules.purchase.trade.dto.response;

import com.stocklog.modules.purchase.trade.type.IndicatorType;

import java.util.List;

public record TradingRuleCheckResponse(
        String decision, // 최종 진입 승인 여부 (PASS 또는 FAIL)
        List<RuleDetail> details, // 개별 규칙 검증 상세 피드
        String verdictComment // AI 종합 검토 의견 레포트 문구
) {
    public record RuleDetail(
            Long ruleId,
            String ruleTitle,
            IndicatorType indicatorType,
            boolean passed // 해당 규칙 통과 여부
    ) {
    }
}
