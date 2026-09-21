package com.stocklog.modules.market.analysis.service.result;

import com.stocklog.modules.market.analysis.dto.MarketRegimeDto;
import com.stocklog.modules.market.analysis.service.analyzer.CreditAnalyzer.CreditDetails;
import com.stocklog.modules.market.analysis.service.analyzer.DivergenceDetector.DivergenceDetails;
import com.stocklog.modules.market.analysis.service.analyzer.LiquidityAnalyzer.LiquidityDetails;
import com.stocklog.modules.market.analysis.service.analyzer.MacroAnalyzer.MacroDetails;
import com.stocklog.modules.market.analysis.service.analyzer.RateAnalyzer.RateDetails;
import com.stocklog.modules.market.analysis.service.analyzer.SectorRotationAnalyzer.RotationDetails;
import com.stocklog.modules.market.analysis.dto.EngineInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MarketRegimeEngine {

    public MarketRegimeDto evaluate(EngineInput input) {
        double weightedScore = getWeightedScore(input);

        // 1. 임계값(Threshold)을 적용한 2차원 축 판별
        String axis1Trend = determineAxis(input.trend().score(), "BULL", "BEAR");
        String axis2Risk = determineAxis(input.risk().score(), "RISK-ON", "RISK-OFF");

        // 2. 기본 국면 문구 매핑
        String combinedRegime = axis1Trend + " + " + axis2Risk;
        String regimeDescription = switch (combinedRegime) {
            case "BULL + RISK-ON" -> "강한 상승장";
            case "BULL + RISK-OFF" -> "상승 추세지만 위험 증가 (경계)";
            case "BEAR + RISK-ON" -> "하락장 속 기술적 반등 / 회복 시도";
            case "BEAR + RISK-OFF" -> "강한 하락장";
            default -> "방향성 탐색 구간 (혼조세)";
        };

        // 3. 단기 모멘텀 급락에 따른 '상승장 내 단기 조정' 오버라이드
        if (axis1Trend.equals("BULL") && (input.momentum().score() <= -40 || input.breadth().score() <= -40)) {
            regimeDescription = "상승 추세 내 단기 조정 (모멘텀/확산도 악화)";
        }

        String macroRegime = extractMacroRegime(input.macro());
        String liquidityRegime = extractLiquidityRegime(input.liquidity());
        String creditRegime = extractCreditRegime(input.credit());
        List<String> leadership = extractLeadership(input.sector());

        List<String> warnings = new ArrayList<>();
        List<String> divergences = new ArrayList<>();

        collectWarnings(input, warnings);
        collectDivergences(input.divergence(), divergences);

        int confidence = (int) Math.round(Math.abs(weightedScore));

        return new MarketRegimeDto(
                regimeDescription,
                axis1Trend,
                axis2Risk,
                input.trend().category().name(),
                macroRegime,
                liquidityRegime,
                creditRegime,
                leadership,
                warnings,
                divergences,
                confidence
        );
    }

    // -20 ~ 20 구간을 중립(NEUTRAL)으로 처리하는 헬퍼 메서드
    private String determineAxis(double score, String positiveLabel, String negativeLabel) {
        if (score >= 20) return positiveLabel;
        if (score <= -20) return negativeLabel;
        return "NEUTRAL";
    }

    private static double getWeightedScore(EngineInput input) {
        double weightedScore =
                (input.trend().score() * 0.15) +
                        (input.momentum().score() * 0.10) +
                        (input.breadth().score() * 0.10) +
                        (input.risk().score() * 0.15) +
                        (input.rates().score() * 0.10) +
                        (input.liquidity().score() * 0.10) +
                        (input.macro().score() * 0.10) +
                        (input.credit().score() * 0.10) +
                        (input.sector().score() * 0.05) +
                        (input.defensive().score() * 0.025) +
                        (input.crossAsset().score() * 0.025);

        weightedScore += input.divergence().score();
        return Math.max(-100.0, Math.min(100.0, weightedScore));
    }

    private String extractMacroRegime(AnalyzerScore macro) {
        if (macro != null && macro.rawDetails() instanceof MacroDetails d) return d.macroRegime();
        return "UNKNOWN";
    }

    private String extractLiquidityRegime(AnalyzerScore liquidity) {
        if (liquidity != null && liquidity.rawDetails() instanceof LiquidityDetails d) return d.liquidityRegime();
        return "UNKNOWN";
    }

    private String extractCreditRegime(AnalyzerScore credit) {
        if (credit != null && credit.rawDetails() instanceof CreditDetails d) return d.creditRegime();
        return "UNKNOWN";
    }

    private List<String> extractLeadership(AnalyzerScore sector) {
        if (sector != null && sector.rawDetails() instanceof RotationDetails d) return d.leadership();
        return List.of();
    }

    private void collectWarnings(EngineInput input, List<String> warnings) {
        if (input.macro().score() < 0) warnings.add("Macro slowdown (거시 경제 둔화)");
        if (input.rates().rawDetails() instanceof RateDetails r && r.isRateShock()) warnings.add("Rate Shock (금리 급등에 따른 밸류에이션 압박)");
        if (input.credit().rawDetails() instanceof CreditDetails c && c.creditRegime().equals("STRESS")) warnings.add("Credit Stress (신용 시장 경색 우려)");
        if (input.breadth().score() < -20) warnings.add("Narrow Market (소수 종목 쏠림 심화)");
    }

    private void collectDivergences(AnalyzerScore divergence, List<String> divergences) {
        if (divergence != null && divergence.rawDetails() instanceof DivergenceDetails d) {
            if (d.warnings() != null && !d.warnings().isEmpty()) divergences.addAll(d.warnings());
            else divergences.add("None");
        }
    }
}