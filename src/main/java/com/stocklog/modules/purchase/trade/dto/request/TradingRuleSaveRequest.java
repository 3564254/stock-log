package com.stocklog.modules.purchase.trade.dto.request;

import com.stocklog.modules.purchase.trade.type.IndicatorType;
import com.stocklog.modules.purchase.trade.type.Operator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TradingRuleSaveRequest(
        @NotBlank(message = "규칙 제목은 필수 항목임")
        String title,

        @NotNull(message = "지표 종류는 필수 선택 항목임")
        IndicatorType indicatorType,

        @NotNull(message = "조건 연산자는 필수 선택 항목임")
        Operator operator,

        Double targetValue // 수치 비교가 필요한 지표용 기준값 (Null 허용)
) {
}
