package com.stocklog.modules.market.analysis.diagnosis;

import com.stocklog.modules.market.domain.type.MarketRegimeType;
import com.stocklog.modules.market.domain.type.RiskAppetiteType;
import com.stocklog.modules.market.domain.type.TrendDirection;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record MarketDiagnosisDto(
        LocalDate date,

        // 수치화된 메인 스코어 및 추세
        double compositeScore,
        double scoreChange1D,
        double scoreChange5D,
        TrendDirection trendDirection,
        int regimeStreakDays,

        // 핵심 국면 판단
        MarketRegimeType regime,
        RiskAppetiteType risk,
        String regimeDescription,
        int confidence,

        // 서브 그룹 점수
        double sectorScore,
        double marketPriceScore,
        double macroLiquidityScore,
        double crossAssetScore,

        // AI 종합 코멘터리
        String comment,
        java.time.LocalDateTime commentAt   // 코멘트를 받은(실패한) 시각. 화면이 '다음 갱신'을 계산한다
) {
    public static MarketDiagnosisDto from(MarketDiagnosis marketDiagnosis) {
        return MarketDiagnosisDto.builder()
                .date(marketDiagnosis.getDate())

                .compositeScore(marketDiagnosis.getCompositeScore())
                .scoreChange1D(marketDiagnosis.getScoreChange1D())
                .scoreChange5D(marketDiagnosis.getScoreChange5D())
                .trendDirection(marketDiagnosis.getTrendDirection())
                .regimeStreakDays(marketDiagnosis.getRegimeStreakDays())

                .regime(marketDiagnosis.getRegime())
                .risk(marketDiagnosis.getRisk())
                .regimeDescription(marketDiagnosis.getRegimeDescription())
                .confidence(marketDiagnosis.getConfidence())

                .sectorScore(marketDiagnosis.getSectorScore())
                .marketPriceScore(marketDiagnosis.getMarketPriceScore())
                .macroLiquidityScore(marketDiagnosis.getMacroLiquidityScore())
                .crossAssetScore(marketDiagnosis.getCrossAssetScore())

                .comment(marketDiagnosis.getComment())
                .commentAt(marketDiagnosis.getCommentAt())
                .build();
    }
}