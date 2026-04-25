package com.stocklog.global.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FinancialUtils {

    public static BigDecimal calculateChangeRate(BigDecimal current, BigDecimal base) {
        if (base.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        return current.subtract(base)
                .divide(base, 4, RoundingMode.HALF_UP) // 정밀도 위해 4자리까지 계산
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);   // 최종 2자리 반올림
    }

    public static BigDecimal calculateChangePrice(BigDecimal current, BigDecimal base) {
        return current.subtract(base);
    }

    public static BigDecimal safeBigDecimal(String val) {
        if (val == null || val.isBlank() || "null".equalsIgnoreCase(val.trim())) {
            return BigDecimal.ZERO;
        }

        try {
            // trim 처리 및 천 단위 구분 기호(,) 제거 후 변환
            return new BigDecimal(val.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            // 로그 기록 또는 기본값 반환
            return BigDecimal.ZERO;
        }
    }
}
