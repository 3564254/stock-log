package com.stocklog.modules.common.finance.model;

import com.stocklog.modules.common.type.Period;
import lombok.Builder;

@Builder
public record Pivot(
        double p, double r1, double r2, double r3,
        double s1, double s2, double s3, Period period
) {
    public static Pivot createFallback(Period period) {
        return new Pivot(0, 0, 0, 0, 0, 0, 0, period);
    }
}