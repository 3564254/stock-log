package com.stocklog.modules.market.analysis.diagnosis;

import com.stocklog.modules.market.domain.type.MacroRegimeType;
import com.stocklog.modules.market.domain.type.MarketRegimeType;
import com.stocklog.modules.market.domain.type.RiskAppetiteType;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record MarketDiagnosisHistoryDto(
        LocalDate date,

        // ── [1] 핵심 시계열 지표 ──
        double compositeScore,
        MarketRegimeType regime,
        int regimeStreakDays,
        RiskAppetiteType risk,
        int confidence,

        // ── [2] 세부 분석기별 시계열 점수 (다중 라인 차트 렌더링 및 통계용) ──
        double trendScore,
        double momentumScore,
        double breadthScore,
        double ratesScore,
        double liquidityScore,
        double macroScore,
        double creditScore,
        double sectorScore,
        double defensiveScore,
        double crossAssetScore,

        // ── [3] 거시 국면 및 경고 플래그 ──
        MacroRegimeType macroRegime,
        String macroNarrative,
        boolean hasBreadthDivergence
) {
    public static MarketDiagnosisHistoryDto from(MarketDiagnosisHistory history) {
        return MarketDiagnosisHistoryDto.builder()
                .date(history.getDate())

                // ── [1] 핵심 시계열 지표 ──
                .compositeScore(history.getCompositeScore())
                .regime(history.getRegime())
                .regimeStreakDays(history.getRegimeStreakDays())
                .risk(history.getRisk())
                .confidence(history.getConfidence())

                // ── [2] 세부 분석기별 시계열 점수 ──
                .trendScore(history.getTrendScore())
                .momentumScore(history.getMomentumScore())
                .breadthScore(history.getBreadthScore())
                .ratesScore(history.getRatesScore())
                .liquidityScore(history.getLiquidityScore())
                .macroScore(history.getMacroScore())
                .creditScore(history.getCreditScore())
                .sectorScore(history.getSectorScore())
                .defensiveScore(history.getDefensiveScore())
                .crossAssetScore(history.getCrossAssetScore())

                // ── [3] 거시 국면 및 경고 플래그 ──
                .macroRegime(history.getMacroRegime())
                .macroNarrative(history.getMacroNarrative())
                .hasBreadthDivergence(history.isHasBreadthDivergence())

                .build();
    }

    public static MarketDiagnosisHistoryDto createFallback() {
        return MarketDiagnosisHistoryDto.builder()
                .date(null)                       // 날짜 없음 = 실제 기록이 아니라는 표시
                .compositeScore(0.0)
                .regime(null)                     // enum 에 NEUTRAL/UNKNOWN 이 있으면 그 값으로 교체
                .regimeStreakDays(0)
                .risk(null)                       // 마찬가지로 중립 값이 있으면 교체
                .confidence(0)
                .trendScore(0.0)
                .momentumScore(0.0)
                .breadthScore(0.0)
                .ratesScore(0.0)
                .liquidityScore(0.0)
                .macroScore(0.0)
                .creditScore(0.0)
                .sectorScore(0.0)
                .defensiveScore(0.0)
                .crossAssetScore(0.0)
                .macroRegime(null)                // 마찬가지
                .macroNarrative(null)
                .hasBreadthDivergence(false)
                .build();
    }
}