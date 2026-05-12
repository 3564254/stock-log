package com.stocklog.global.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FinancialUtils {

    /**
     * 값이 유효한지(null이 아니고 NA가 아닌지) 검사하는 유틸 메서드
     */
    public static boolean isAvailable(BigDecimal value) {
        // equals 대신 compareTo를 쓰면 소수점(예: -1.0과 -1) 차이로 인한 오류까지 방지할 수 있습니다.
        return value != null && value.compareTo(BigDecimal.ZERO) != 0;
    }

    /**
     * 등락률 계산
     */
    public static BigDecimal calculateChangeRate(BigDecimal current, BigDecimal base) {
        // 1. 데이터가 하나라도 없으면 계산 불가 (N/A 반환)
        if (!isAvailable(current) || !isAvailable(base)) {
            return BigDecimal.ZERO;
        }

        // 2. 기준가가 0원이면 수학적으로 나눌 수 없으므로 예외 처리 (기존 ZERO에서 NA로 변경 추천)
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return current.subtract(base)
                .divide(base, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 등락폭 계산
     */
    public static BigDecimal calculateChangePrice(BigDecimal current, BigDecimal base) {
        // 데이터가 하나라도 없으면 계산 불가 (N/A 반환)
        if (!isAvailable(current) || !isAvailable(base)) {
            return BigDecimal.ZERO;
        }

        return current.subtract(base);
    }

    /**
     * 문자열을 BigDecimal로 안전하게 변환 (safeBigDecimal과 parseSafe 병합)
     */
    public static BigDecimal safeParse(String val) {
        if (val == null || val.isBlank() || "null".equalsIgnoreCase(val.trim())) {
            return BigDecimal.ZERO; // 기존 ZERO나 null 대신 NA 사용
        }

        try {
            // trim 처리 및 천 단위 구분 기호(,) 제거 후 변환
            return new BigDecimal(val.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            // 숫자 포맷이 아닐 경우 N/A 처리
            // 필요에 따라 log.warn("BigDecimal 변환 실패: {}", val); 추가
            return BigDecimal.ZERO;
        }
    }
}