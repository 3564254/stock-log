package com.stocklog.modules.common.finance.model;


public record TargetResult(
        double primaryEntry,
        double secondaryEntry,
        double stopLoss,
        double primaryTarget,
        double secondaryTarget,
        double trailingStop,
        double riskPerShare,
        double recommendedWeight,
        double riskRewardRatio,
        double volatilityRatio
) {
    /** 가격/ATR 계산이 불가능할 때 사용하는 안전한 기본값 */
    public static TargetResult createFallback() {
        return new TargetResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}

