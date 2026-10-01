package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.computeReturn;
import static com.stocklog.modules.market.analysis.service.analyzer.utils.GetDoublesUtils.getDoubles;

@Component
public class DefensiveAnalyzer implements MarketAnalyzer {

    // 분석된 세부 수익률 및 다이버전스 경고 여부를 담는 레코드
    public record DefensiveDetails(
            double staplesReturn,
            //double utilityReturn,
            double goldReturn,
            double dollarReturn,
            double growthReturn,
            boolean defensiveDivergence,
            String defensiveRegime
    ) {}

    @Override
    public MarketAnalyzerScore analyze(Map<IndexType,MarketChartData> data) {
        // 1. 방어 자산 데이터 추출
        List<Double> staplesCloses = getDoubles(data, IndexType.NASDAQ_CONSUMER_STAPLES);
        //List<Double> utilCloses = getDoubles(data, IndexType.DOW_JONES_UTILITY);
        List<Double> goldCloses = getDoubles(data, IndexType.PHLX_GOLD_SILVER);
        List<Double> dollarCloses = getDoubles(data, IndexType.DOLLAR_INDEX);

        // 2. 시장 전체 및 성장주(Growth) 데이터 추출 (비교용)
        List<Double> sp500Closes = getDoubles(data, IndexType.SP500);
        List<Double> nasdaqCloses = getDoubles(data, IndexType.NASDAQ_100);

        // 수익률 계산 (20일 단기/중기 모멘텀 기준)
        double staplesReturn = calculateReturn(staplesCloses);
        //double utilReturn = calculateReturn(utilCloses);
        double goldReturn = calculateReturn(goldCloses);
        double dollarReturn = calculateReturn(dollarCloses);

        double sp500Return = calculateReturn(sp500Closes);
        double growthReturn = calculateReturn(nasdaqCloses);

        // 3. 방어적 로테이션 점수 산출 (-100 ~ +100)
        double score = calculateDefensiveScore(staplesReturn, goldReturn, dollarReturn, growthReturn);

        // 4. 경계 신호 (Defensive Divergence) 포착
        // 주식시장(SP500)이 표면적으로 상승함에도 방어자산이 동반 강세를 보이는 현상 체크
        boolean isMarketUp = sp500Return > 0;
        int strongDefensiveCount = 0;
        if (staplesReturn > 0) strongDefensiveCount++;
        if (goldReturn > 0) strongDefensiveCount++;
        if (dollarReturn > 0) strongDefensiveCount++;

        // 4개의 주요 방어 자산 중 2개 이상이 상승하면 강력한 자금 대피 신호로 간주
        boolean defensiveDivergence = (isMarketUp && strongDefensiveCount >= 2);

        String regime = determineRegime(score, defensiveDivergence);
        ScoreCategory category = MarketAnalyzerScore.categorize(score);

        DefensiveDetails details = new DefensiveDetails(
                staplesReturn, goldReturn, dollarReturn, growthReturn, defensiveDivergence, regime
        );

        return new MarketAnalyzerScore("DefensiveRotation", score, category, details);
    }

    private double calculateDefensiveScore(double staples, double gold, double dollar, double growth) {
        double score = 0;

        // [1] 방어 자산 강세 시 감점 (Risk-Off) - 각 20점씩 총 60점
        if (staples > 0) score -= 20;
        else score += 20; // 방어주 하락 시 Risk-On 가산

        if (gold > 0) score -= 20;
        else score += 20; // 안전자산(금) 하락 시 Risk-On 가산

        if (dollar > 0) score -= 20;
        else score += 20; // 달러 약세 시 Risk-On 가산

        // [2] 성장주 절대 모멘텀 (최대 ±25점)
        if (growth < 0) score -= 25;
        else score += 25;

        // [3] 상대 강도 (최대 ±15점)
        if (staples > growth) score -= 15; // 방어주가 성장주를 이길 때 경계 감점
        else score += 15;                  // 성장주가 방어주를 아웃퍼폼할 때 가산

        // -100.0 ~ +100.0 범위 클리핑
        return Math.max(-100.0, Math.min(100.0, score));
    }

    private String determineRegime(double score, boolean defensiveDivergence) {
        // 다이버전스 발생 시 점수와 무관하게 즉각적인 경계 신호 발송
        if (defensiveDivergence) return "WARNING_DEFENSIVE_DIVERGENCE";
        if (score <= -40) return "DEFENSIVE_ROTATION";
        if (score >= 40) return "RISK_ON_GROWTH";
        return "NEUTRAL";
    }

    private double calculateReturn(List<Double> closes) {
        if (closes == null || closes.size() < 21) return 0.0;
        return computeReturn(closes, closes.size() - 1, 20);
    }
}