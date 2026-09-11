package com.stocklog.modules.purchase.trade.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.BiFunction;

@Getter
@RequiredArgsConstructor
@Slf4j
public enum Operator {
    GREATER_THAN(">", (val, ref) -> val > ref),
    GREATER_THAN_OR_EQUAL_TO(">=", (val, ref) -> val >= ref),
    LESS_THAN("<", (val, ref) -> val < ref),
    LESS_THAN_OR_EQUAL_TO("<=", (val, ref) -> val <= ref),
    EQUAL_TO("==", (val, ref) -> val.equals(ref));

    private final String symbol;
    private final BiFunction<Double, Double, Boolean> expression;

    // 실제 기술적 지표 값과 규칙의 기준값을 비교 평가하는 도메인 메서드
    public boolean evaluate(double targetValue, double referenceValue) {
        log.info("target:{} {} ref:{}", targetValue, this.expression, referenceValue);
        return this.expression.apply(targetValue, referenceValue);
    }
}