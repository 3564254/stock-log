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
    /** 가격/ATR 계산이 불가능할 때 사용하는 안전한 기본값. */
    public static TargetResult createFallback() {
        return new TargetResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}

/**
 * @param primaryEntry     1차 진입가
 * @param secondaryEntry   2차(분할) 진입가
 * @param stopLoss         손절가
 * @param primaryTarget    1차 목표가
 * @param secondaryTarget  2차 목표가
 * @param trailingStop     추적 손절선
 * @param riskPerShare     주당 리스크 (primaryEntry - stopLoss)
 * @param recommendedWeight 국면/손익비 기준 권장 비중 (0~100)
 * @param riskRewardRatio  손익비 (reward / risk)
 * @param volatilityRatio  변동성 비율, ATR/현재가 * 100 (%)
 */
