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
        double pocPrice,
        double volumeRatio,          // projectedVolume / volumeMa20 (20일 평균 대비 배수)
        boolean projectionReliable,  // false면 장 초반이라 거래량 판단을 보류한 상태 (플래그는 모두 false)
        boolean isIntraday           // true면 projectedVolume은 장중 예상치
) {
    /** 기존 9개 필드 생성자 호환용 (새 필드는 "장 마감 후 확정 값" 기본값). */
    public VolumeAnalysis(double currentVolume, double projectedVolume, double volumeMa5, double volumeMa20,
                          boolean isVolBullish, boolean isVolBearish, boolean isVolumeDryingUp,
                          double vwap20, double pocPrice) {
        this(currentVolume, projectedVolume, volumeMa5, volumeMa20,
                isVolBullish, isVolBearish, isVolumeDryingUp, vwap20, pocPrice,
                volumeMa20 > 0 ? projectedVolume / volumeMa20 : 0, true, false);
    }

    public static VolumeAnalysis createFallback() {
        return new VolumeAnalysis(0, 0, 0, 0, false, false, false, 0, 0);
    }
}