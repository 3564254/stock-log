package com.stocklog.modules.common.finance.model;

import lombok.Builder;

@Builder
public record BollingerBand(
        double stdDev20,
        double upper,
        double lower,
        double bandWidth,
        double percentB,
        double dynamicThreshold,
        boolean isSqueeze
) {
    public static BollingerBand createFallback() {
        return new BollingerBand(0, 0, 0, 0, 0, 0, false);
    }
}