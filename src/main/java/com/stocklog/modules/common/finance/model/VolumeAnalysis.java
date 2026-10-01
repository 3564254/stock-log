package com.stocklog.modules.common.finance.model;

import lombok.Builder;

@Builder
public record VolumeAnalysis(
        double currentVolume,
        double projectedVolume,
        double volumeMa5,
        double volumeMa20,
        boolean isVolBullish,
        boolean isVolBearish,
        boolean isVolumeDryingUp,
        double vwap20,
        double pocPrice
) {
    public static VolumeAnalysis createFallback() {
        return new VolumeAnalysis(0, 0, 0, 0, false, false, false, 0, 0);
    }
}