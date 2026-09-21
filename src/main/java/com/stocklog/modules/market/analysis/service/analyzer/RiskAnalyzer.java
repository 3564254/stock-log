package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.market.analysis.service.result.AnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.stocklog.modules.market.analysis.service.analyzer.utils.GetDoublesUtils.getDoubles;

@Component
public class RiskAnalyzer implements MarketAnalyzer {

    // 분석에 사용할 지표 목록
    private final List<IndexType> indexTypeList = List.of(
            IndexType.VIX, IndexType.HIGH_YIELD_SPREAD,
            IndexType.DOLLAR_INDEX, IndexType.USD_JPY, IndexType.BITCOIN
    );

    // 세부 점수를 결과에 담기 위한 레코드
    public record RiskDetails(
            double vixScore,
            double hySpreadScore,
            double dollarScore,
            double usdJpyScore,
            double bitcoinScore
    ) {}

    @Override
    public AnalyzerScore analyze(Map<IndexType,MarketChartData> data) {
        // MarketChartData에서 각 지표별 종가(close) 리스트를 추출한다고 가정 (구현체에 맞게 수정 필요)
        List<Double> vixCloses = getDoubles(data, IndexType.VIX);
        List<Double> hySpreadCloses = getDoubles(data, IndexType.HIGH_YIELD_SPREAD);
        List<Double> dollarCloses = getDoubles(data, IndexType.DOLLAR_INDEX);
        List<Double> usdJpyCloses = getDoubles(data, IndexType.USD_JPY);
        List<Double> bitcoinCloses = getDoubles(data, IndexType.BITCOIN);

        // 각 팩터별 점수 계산
        double vixScore = analyzeVix(vixCloses);
        double hySpreadScore = analyzeHySpread(hySpreadCloses);
        double dollarScore = analyzeDollarIndex(dollarCloses);
        double usdJpyScore = analyzeUsdJpy(usdJpyCloses);
        double bitcoinScore = analyzeBitcoin(bitcoinCloses);

        // 총합 계산 및 -100 ~ 100 정규화
        double totalScore = vixScore + hySpreadScore + dollarScore + usdJpyScore + bitcoinScore;
        totalScore = Math.max(-100.0, Math.min(100.0, totalScore));

        ScoreCategory category = categorizeRisk(totalScore);
        RiskDetails details = new RiskDetails(vixScore, hySpreadScore, dollarScore, usdJpyScore, bitcoinScore);

        return new AnalyzerScore("RiskAppetite", totalScore, category, details);
    }

    private double analyzeVix(List<Double> closes) {
        if (closes == null || closes.size() <= 20) return 0.0;

        double score = 0;
        int lastIdx = closes.size() - 1;
        double cur = closes.get(lastIdx);
        double vix5D = closes.get(lastIdx - 5);
        double vix20D = closes.get(lastIdx - 20);

        // Level (최대 ±10점)
        if (cur < 20) score += 10;
        else if (cur > 25 && cur < 30) score -= 5;
        else if (cur >= 30) score -= 10;

        // 5D Change (하락 시 공포 완화 -> Risk-On)
        if (cur < vix5D) score += 10;
        else score -= 10;

        // 20D Change
        if (cur < vix20D) score += 10;
        else score -= 10;

        return score;
    }

    private double analyzeHySpread(List<Double> closes) {
        if (closes == null || closes.size() <= 20) return 0.0;

        double score = 0;
        int lastIdx = closes.size() - 1;
        double cur = closes.get(lastIdx);
        double hy5D = closes.get(lastIdx - 5);
        double hy20D = closes.get(lastIdx - 20);

        // Level (최대 ±10점)
        if (cur < 4.0) score += 10;
        else if (cur >= 5.0) score -= 10;

        // 5D Change (스프레드 축소 시 신용 위험 감소 -> Risk-On)
        if (cur < hy5D) score += 10;
        else score -= 10;

        // 20D Change
        if (cur < hy20D) score += 10;
        else score -= 10;

        return score;
    }

    private double analyzeDollarIndex(List<Double> closes) {
        if (closes == null || closes.size() <= 20) return 0.0;
        int lastIdx = closes.size() - 1;

        double cur = closes.get(lastIdx);
        double prev20D = closes.get(lastIdx - 20);

        // 달러 약세(하락) 시 Risk-On (최대 ±15점)
        return (cur < prev20D) ? 15.0 : -15.0;
    }

    private double analyzeUsdJpy(List<Double> closes) {
        if (closes == null || closes.size() <= 20) return 0.0;
        int lastIdx = closes.size() - 1;

        double cur = closes.get(lastIdx);
        double prev20D = closes.get(lastIdx - 20);

        // 환율 상승(엔화 약세) 시 Risk-On (최대 ±10점)
        return (cur > prev20D) ? 10.0 : -10.0;
    }

    private double analyzeBitcoin(List<Double> closes) {
        if (closes == null || closes.size() <= 20) return 0.0;
        int lastIdx = closes.size() - 1;

        double cur = closes.get(lastIdx);
        double prev20D = closes.get(lastIdx - 20);

        // 비트코인 상승 시 위험 선호 심리 증가 Risk-On (최대 ±15점)
        return (cur > prev20D) ? 15.0 : -15.0;
    }

    // 설계 문서 7항의 5단계 국면 분리에 맞춘 카테고리화 매핑
    private ScoreCategory categorizeRisk(double score) {
        if (score >= 60) return ScoreCategory.STRONG_POSITIVE; // EXTREME_RISK_ON 대체
        if (score >= 20) return ScoreCategory.POSITIVE;        // RISK_ON 대체
        if (score > -20) return ScoreCategory.NEUTRAL;         // NEUTRAL
        if (score > -60) return ScoreCategory.NEGATIVE;        // RISK_OFF 대체
        return ScoreCategory.STRONG_NEGATIVE;                  // EXTREME_RISK_OFF 대체
    }

}