package com.stocklog.modules.common.finance.model;

import lombok.Builder;

@Builder
public record MomentumMetrics(
        double rsi,
        double atr,
        double adx,
        boolean isStrongTrend,
        boolean isWeakTrend
) {
    public static MomentumMetrics createFallback() {
        return new MomentumMetrics(0, 0, 0, false, false);
    }
}