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
public class RateAnalyzer implements MarketAnalyzer {

    // 각 금리 지표별 점수 및 시장 충격 여부를 담는 레코드
    public record RateDetails(
            double score10Y,
            double score2Y,
            double scoreYieldCurve,
            double scoreFedFunds,
            boolean isRateShock,
            String rateRegime
    ) {}

    @Override
    public MarketAnalyzerScore analyze(Map<IndexType,MarketChartData> data) {
        // 1. 금리 및 주식시장 데이터 추출
        List<Double> treasury10Y = getDoubles(data, IndexType.US_TREASURY_10Y);
        List<Double> treasury2Y = getDoubles(data, IndexType.US_TREASURY_2Y);
        List<Double> yieldCurve = getDoubles(data, IndexType.T10Y2Y);
        List<Double> fedFunds = getDoubles(data, IndexType.US_FED_FUNDS_RATE);
        List<Double> nasdaq = getDoubles(data, IndexType.NASDAQ_100);

        // 2. 금리와 나스닥의 관계를 통한 Rate Shock(금리 발작) 감지
        boolean isRateShock = detectRateShock(treasury10Y, nasdaq);

        // 3. 팩터별 점수 계산
        double score10Y = analyze10Y(treasury10Y, nasdaq, isRateShock);
        double score2Y = analyze2Y(treasury2Y);
        double scoreYieldCurve = analyzeYieldCurve(yieldCurve);
        double scoreFedFunds = analyzeFedFunds(fedFunds);

        // 4. 총합 점수 산출 (-100 ~ +100 정규화)
        double totalScore = score10Y + score2Y + scoreYieldCurve + scoreFedFunds;
        totalScore = Math.max(-100.0, Math.min(100.0, totalScore));

        // 5. 금리 환경(Regime) 판별
        String regime = determineRateRegime(totalScore, isRateShock);
        ScoreCategory category = MarketAnalyzerScore.categorize(totalScore);

        RateDetails details = new RateDetails(
                score10Y, score2Y, scoreYieldCurve, scoreFedFunds, isRateShock, regime
        );

        return new MarketAnalyzerScore("InterestRates", totalScore, category, details);
    }

    /**
     * [팩터 1] 10년물 국채 금리 (최대 ±35점)
     * 금리 수준 + 변화 방향 + 주식시장 연동성 통합 분석
     */
    private double analyze10Y(List<Double> t10y, List<Double> nasdaq, boolean isRateShock) {
        if (t10y == null || t10y.size() < 21) return 0.0;
        double score = 0;
        int lastIdx = t10y.size() - 1;

        double cur = t10y.get(lastIdx);
        double prev5 = t10y.get(lastIdx - 5);
        double prev20 = t10y.get(lastIdx - 20);

        // 절대 레벨 (4.5% 이상이면 증시에 부담)
        if (cur > 4.5) score -= 10;
        else if (cur < 4.0) score += 10;

        // 변화 방향 및 속도 평가 (Δ5D, Δ20D)
        double delta5 = cur - prev5;
        double delta20 = cur - prev20;

        if (delta5 > 0.2) score -= 10; // 5일 내 급등
        else if (delta5 < -0.1) score += 10; // 하락 안정화

        if (delta20 > 0.4) score -= 5;
        else if (delta20 < -0.2) score += 5;

        // 주식시장 민감도 융합: Rate Shock 상태면 페널티 부여
        if (isRateShock) score -= 10;

        // 금리가 오르지만(0 초과) 나스닥도 오르는 경우 (시장 감내 상태) 페널티 상쇄
        if (delta20 > 0 && !isRateShock && isNasdaqRising(nasdaq)) {
            score += 10;
        }

        return score;
    }

    /**
     * [팩터 2] 2년물 국채 금리 (최대 ±25점)
     * 단기 유동성 및 연준 정책 기대감 반영
     */
    private double analyze2Y(List<Double> t2y) {
        if (t2y == null || t2y.size() < 21) return 0.0;
        double score = 0;
        int lastIdx = t2y.size() - 1;

        double cur = t2y.get(lastIdx);
        double prev5 = t2y.get(lastIdx - 5);
        double prev20 = t2y.get(lastIdx - 20);

        // 절대 레벨 (5% 이상 단기 자금 경색 우려)
        if (cur > 5.0) score -= 10;
        else if (cur < 4.5) score += 10;

        // 단기 금리 하락 시 유동성 개선 기대
        if (cur < prev5) score += 10;
        else score -= 10;

        if (cur < prev20) score += 5;
        else score -= 5;

        return score;
    }

    /**
     * [팩터 3] 장단기 금리차 10Y-2Y (최대 ±25점)
     * 수익률 곡선 역전 여부 판별
     */
    private double analyzeYieldCurve(List<Double> t10y2y) {
        if (t10y2y == null || t10y2y.size() < 21) return 0.0;
        double score = 0;
        int lastIdx = t10y2y.size() - 1;

        double cur = t10y2y.get(lastIdx);
        double prev20 = t10y2y.get(lastIdx - 20);

        // 역전 상태(음수) 시 침체 우려로 감점
        if (cur < 0) score -= 15;
        else score += 15;

        // 커브가 가파르게 변하며 정상화되는 과정이면 가산점
        if (cur > prev20) score += 10;
        else score -= 10;

        return score;
    }

    /**
     * [팩터 4] 기준금리 (최대 ±15점)
     * 연준 정책 방향
     */
    private double analyzeFedFunds(List<Double> fedFunds) {
        if (fedFunds == null || fedFunds.size() < 2) return 0.0;
        int lastIdx = fedFunds.size() - 1;

        double cur = fedFunds.get(lastIdx);
        double prev = fedFunds.get(lastIdx - 1);

        if (cur < prev) return 15.0;  // 금리 인하
        if (cur > prev) return -15.0; // 금리 인상
        return 0.0;                   // 동결
    }

    /**
     * 10년물 급등 + 나스닥 급락 발생 시 Rate Shock로 판단
     */
    private boolean detectRateShock(List<Double> t10y, List<Double> nasdaq) {
        if (t10y == null || nasdaq == null || t10y.size() < 6 || nasdaq.size() < 6) return false;
        int lastIdx = t10y.size() - 1;

        double t10yDelta5 = t10y.get(lastIdx) - t10y.get(lastIdx - 5);
        double nasdaqReturn5 = computeReturn(nasdaq, nasdaq.size() - 1, 5);

        // 10년물이 5일 내 급등하고(0.2%p 이상), 나스닥이 2% 이상 하락할 때 압박 상태로 기록
        return t10yDelta5 >= 0.2 && nasdaqReturn5 <= -2.0;
    }

    private boolean isNasdaqRising(List<Double> nasdaq) {
        if (nasdaq == null || nasdaq.size() < 21) return false;
        return computeReturn(nasdaq, nasdaq.size() - 1, 20) > 0;
    }

    private String determineRateRegime(double score, boolean isRateShock) {
        if (isRateShock) return "RATE_SHOCK_PRESSURE";
        if (score >= 40) return "SUPPORTIVE_RATES";
        if (score <= -40) return "RESTRICTIVE_RATES";
        return "NEUTRAL_RATES";
    }

}