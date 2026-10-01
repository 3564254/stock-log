package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.stocklog.modules.market.analysis.service.analyzer.utils.GetDoublesUtils.getDoubles;

@Component
public class MacroAnalyzer implements MarketAnalyzer {

    // 세부 점수 및 최종 판단된 경기 국면을 담는 레코드
    public record MacroDetails(
            double inflationScore,
            double employmentScore,
            double economyScore,
            String macroRegime
    ) {}

    @Override
    public MarketAnalyzerScore analyze(Map<IndexType,MarketChartData> data) {
        // 1. 물가 (Inflation)
        List<Double> cpiCloses = getDoubles(data, IndexType.US_CPI);
        List<Double> pceCloses = getDoubles(data, IndexType.US_CORE_PCE);

        // 2. 고용 (Employment)
        List<Double> unempCloses = getDoubles(data, IndexType.US_UNEMPLOYMENT_RATE);
        List<Double> nfpCloses = getDoubles(data, IndexType.US_NONFARM_PAYROLLS);
        List<Double> claimsCloses = getDoubles(data, IndexType.US_INITIAL_CLAIMS);

        // 3. 경기/소비 (Economy & Consumption)
        List<Double> gdpCloses = getDoubles(data, IndexType.US_GDP);
        List<Double> retailCloses = getDoubles(data, IndexType.US_RETAIL_SALES);
        List<Double> sentimentCloses = getDoubles(data, IndexType.US_CONSUMER_SENTIMENT);
        List<Double> indproCloses = getDoubles(data, IndexType.US_INDPRO);

        // 팩터별 점수 계산
        double inflationScore = analyzeInflation(cpiCloses, pceCloses);
        double employmentScore = analyzeEmployment(unempCloses, nfpCloses, claimsCloses);
        double economyScore = analyzeEconomy(gdpCloses, retailCloses, sentimentCloses, indproCloses);

        // 총합 계산 및 -100 ~ +100 정규화
        double totalScore = inflationScore + employmentScore + economyScore;
        totalScore = Math.max(-100.0, Math.min(100.0, totalScore));

        // 지표들의 세부 방향을 바탕으로 5단계 국면(Regime) 판별
        String regime = determineMacroRegime(totalScore, inflationScore, employmentScore, economyScore);

        ScoreCategory category = MarketAnalyzerScore.categorize(totalScore);
        MacroDetails details = new MacroDetails(inflationScore, employmentScore, economyScore, regime);

        return new MarketAnalyzerScore("MacroCycle", totalScore, category, details);
    }

    /**
     * [팩터 1] 물가 분석 (최대 ±20점)
     * 물가 상승 폭이 둔화되거나 하락하면 긍정적(Risk-On/Expansion 환경), 상승 가속 시 부정적(Stagflation/금리 인상 우려)
     */
    private double analyzeInflation(List<Double> cpi, List<Double> pce) {
        double score = 0;
        // CPI (±10점)
        score += evaluateNegativeIndicator(cpi, 10.0);
        // Core PCE (±10점)
        score += evaluateNegativeIndicator(pce, 10.0);
        return score;
    }

    /**
     * [팩터 2] 고용 분석 (최대 ±40점)
     * 실업률과 실업수당 청구 건수는 낮아져야 긍정적, 비농업 고용은 증가해야 긍정적
     */
    private double analyzeEmployment(List<Double> unemp, List<Double> nfp, List<Double> claims) {
        double score = 0;
        // 실업률 (역지표, ±15점)
        score += evaluateNegativeIndicator(unemp, 15.0);
        // 비농업 고용 (정지표, ±15점)
        score += evaluatePositiveIndicator(nfp, 15.0);
        // 신규 실업수당 청구 (역지표, ±10점)
        score += evaluateNegativeIndicator(claims, 10.0);
        return score;
    }

    /**
     * [팩터 3] 경기 및 소비 분석 (최대 ±40점)
     * GDP, 소매판매, 산업생산, 소비심리 모두 성장 및 가속 여부 확인
     */
    private double analyzeEconomy(List<Double> gdp, List<Double> retail, List<Double> sentiment, List<Double> indpro) {
        double score = 0;
        // GDP (정지표, ±10점)
        score += evaluatePositiveIndicator(gdp, 10.0);
        // 소매판매 (정지표, ±10점)
        score += evaluatePositiveIndicator(retail, 10.0);
        // 산업생산 (정지표, ±10점)
        score += evaluatePositiveIndicator(indpro, 10.0);
        // 소비심리 (정지표, ±10점)
        score += evaluatePositiveIndicator(sentiment, 10.0);
        return score;
    }

    /**
     * 정지표(높아지면 좋은 지표: GDP, 고용, 소매판매 등) 점수 평가
     * 절대값 상승(+50%) 및 과거 대비 상승폭 가속 여부(+50%)를 함께 반영
     */
    private double evaluatePositiveIndicator(List<Double> closes, double maxScore) {
        if (closes == null || closes.size() < 3) return 0.0;

        int lastIdx = closes.size() - 1;
        double cur = closes.get(lastIdx);
        double prev1 = closes.get(lastIdx - 1);
        double prev2 = closes.get(lastIdx - 2);

        double score = 0;
        double currentDelta = cur - prev1;
        double pastDelta = prev1 - prev2;

        // 1. 방향 (증가하면 점수 부여)
        if (currentDelta > 0) score += (maxScore * 0.5);
        else score -= (maxScore * 0.5);

        // 2. 가속도 (성장 폭이 더 커지면 점수 추가, 둔화되면 삭감)
        if (currentDelta > pastDelta) score += (maxScore * 0.5);
        else score -= (maxScore * 0.5);

        return score;
    }

    /**
     * 역지표(낮아져야 좋은 지표: 실업률, CPI, 실업수당 청구 등) 점수 평가
     */
    private double evaluateNegativeIndicator(List<Double> closes, double maxScore) {
        if (closes == null || closes.size() < 3) return 0.0;

        int lastIdx = closes.size() - 1;
        double cur = closes.get(lastIdx);
        double prev1 = closes.get(lastIdx - 1);
        double prev2 = closes.get(lastIdx - 2);

        double score = 0;
        double currentDelta = cur - prev1;
        double pastDelta = prev1 - prev2;

        // 1. 방향 (감소하면 긍정적)
        if (currentDelta < 0) score += (maxScore * 0.5);
        else score -= (maxScore * 0.5);

        // 2. 가속도 (상승 폭 둔화 시 긍정적)
        if (currentDelta < pastDelta) score += (maxScore * 0.5);
        else score -= (maxScore * 0.5);

        return score;
    }

    /**
     * 문서 10항에 명시된 5가지 경기 사이클 도출 로직
     */
    private String determineMacroRegime(double totalScore, double inflationScore, double employmentScore, double economyScore) {
        boolean isEconomyStrong = economyScore > 10;
        boolean isEconomySlowing = economyScore < -10;
        boolean isEmploymentWeakening = employmentScore < -10;
        boolean isInflationRising = inflationScore < 0; // 역지표이므로 점수가 음수면 물가 상승 가속

        // CONTRACTION: 경기 하강 및 고용 악화가 뚜렷한 심각한 수축기
        if (totalScore <= -50) {
            return "CONTRACTION";
        }
        // SLOWDOWN: 경기가 둔화되고 고용이 약해지기 시작하는 시기
        else if (isEconomySlowing || isEmploymentWeakening) {
            return "SLOWDOWN";
        }
        // LATE_CYCLE: 경기는 아직 버티지만 물가 상승 압력이 높은 후반부 국면
        else if (isInflationRising && !isEconomyStrong) {
            return "LATE_CYCLE";
        }
        // EXPANSION: 경기 성장과 고용 호조가 동반되는 확장기
        else if (totalScore >= 40 || isEconomyStrong) {
            return "EXPANSION";
        }
        // RECOVERY: 침체 후 서서히 지표가 회복되는 국면
        else {
            return "RECOVERY";
        }
    }

}