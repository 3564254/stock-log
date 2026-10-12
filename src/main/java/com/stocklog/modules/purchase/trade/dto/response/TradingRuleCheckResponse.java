package com.stocklog.modules.purchase.trade.dto.response;

import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.purchase.trade.type.IndicatorType;

import java.util.List;

public record TradingRuleCheckResponse(
        String decision, // 최종 진입 승인 여부 (PASS 또는 FAIL)
        List<RuleDetail> details, // 개별 규칙 검증 상세 피드
        String verdictComment, // 종합 분석 리포트 문구
        ChartPhase chartPhase, // 타점 엔진이 판정한 차트 단계 (화면 목록 즉시 갱신용)
        Double targetPrice,    // 1차 진입가. 진입 계획이 없으면 0.0
        String signalVerdict   // 타점 엔진 판정 (ACTIONABLE, WAIT_FOR_ZONE 등)
) {
    public record RuleDetail(
            Long ruleId,
            String ruleTitle,
            IndicatorType indicatorType,
            boolean passed // 해당 규칙 통과 여부
    ) {
    }
}
