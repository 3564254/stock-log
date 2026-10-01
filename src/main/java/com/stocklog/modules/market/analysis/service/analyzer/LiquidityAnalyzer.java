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
public class LiquidityAnalyzer implements MarketAnalyzer {

    // 각 지표별 세부 점수와 최종 유동성 국면을 담는 레코드
    public record LiquidityDetails(
            double fedAssetsScore,
            double m2Score,
            double rrpScore,
            double fedFundsScore,
            String liquidityRegime
    ) {}

    @Override
    public MarketAnalyzerScore analyze(Map<IndexType,MarketChartData> data) {
        // 1. 유동성 관련 핵심 지표 데이터 추출
        List<Double> fedAssetsCloses = getDoubles(data, IndexType.FED_TOTAL_ASSETS);
        List<Double> rrpCloses = getDoubles(data, IndexType.REVERSE_REPO);
        List<Double> m2Closes = getDoubles(data, IndexType.US_M2);
        List<Double> fedFundsCloses = getDoubles(data, IndexType.US_FED_FUNDS_RATE);

        // 2. 팩터별 유동성 점수 계산 (Momentum & Acceleration 반영)
        double fedAssetsScore = analyzeFedAssets(fedAssetsCloses);
        double m2Score = analyzeM2(m2Closes);
        double rrpScore = analyzeReverseRepo(rrpCloses);
        double fedFundsScore = analyzeFedFundsRate(fedFundsCloses);

        // 3. 유동성 총합 점수 산출 (-100 ~ +100 정규화)
        double totalScore = fedAssetsScore + m2Score + rrpScore + fedFundsScore;
        totalScore = Math.max(-100.0, Math.min(100.0, totalScore));

        // 4. 점수에 따른 유동성 환경(Regime) 판별
        String regime = determineLiquidityRegime(totalScore);
        ScoreCategory category = MarketAnalyzerScore.categorize(totalScore);

        LiquidityDetails details = new LiquidityDetails(
                fedAssetsScore, m2Score, rrpScore, fedFundsScore, regime
        );

        return new MarketAnalyzerScore("Liquidity", totalScore, category, details);
    }

    /**
     * [팩터 1] 연준 총자산 (최대 ±30점)
     * 양적완화(QE)/양적긴축(QT) 방향 판단. 자산 증가 시 긍정적.
     */
    private double analyzeFedAssets(List<Double> closes) {
        if (closes == null || closes.size() < 41) return 0.0;

        // Momentum (최근 20일 변동) 및 Acceleration (이전 20일 대비 변동폭)
        double currentMomentum = computeReturn(closes, closes.size() - 1, 20);
        double pastMomentum = computeReturn(closes, closes.size() - 21, 20);
        double acceleration = currentMomentum - pastMomentum;

        double score = 0;
        if (currentMomentum > 0) score += 15;
        else score -= 15;

        // 가속도: 자산 증가 속도가 빨라지면 추가 점수, 축소 속도가 빨라지면 감점
        if (acceleration > 0) score += 15;
        else score -= 15;

        return score;
    }

    /**
     * [팩터 2] 통화량 M2 (최대 ±30점)
     * 시중 통화량 증가 시 긍정적. 단, 둔화 시 감점 적용.
     */
    private double analyzeM2(List<Double> closes) {
        if (closes == null || closes.size() < 41) return 0.0;

        double currentMomentum = computeReturn(closes, closes.size() - 1, 20);
        double pastMomentum = computeReturn(closes, closes.size() - 21, 20);
        double acceleration = currentMomentum - pastMomentum;

        double score = 0;
        // M2 증가 시 긍정적
        if (currentMomentum > 0) score += 15;
        else score -= 15;

        // 문서 9항 요구사항: "M2 둔화 -> 유동성 악화"
        // 증가하더라도 과거 대비 증가율이 떨어졌다면(둔화) 감점 처리
        if (acceleration > 0) score += 15;
        else score -= 15;

        return score;
    }

    /**
     * [팩터 3] 역레포 잔고 RRP (최대 ±30점)
     * 시중 유동성을 흡수하는 역지표. RRP 감소 시 유동성 개선으로 평가.
     */
    private double analyzeReverseRepo(List<Double> closes) {
        if (closes == null || closes.size() < 41) return 0.0;

        double currentMomentum = computeReturn(closes, closes.size() - 1, 20);
        double pastMomentum = computeReturn(closes, closes.size() - 21, 20);
        double acceleration = currentMomentum - pastMomentum;

        double score = 0;
        // 역지표이므로 감소(음수) 시 긍정적
        if (currentMomentum < 0) score += 15;
        else score -= 15;

        // 감소 가속도 평가
        if (acceleration < 0) score += 15;
        else score -= 15;

        return score;
    }

    /**
     * [팩터 4] 기준금리 (최대 ±10점)
     * 금리 인하(하락) 시 유동성 환경 개선.
     */
    private double analyzeFedFundsRate(List<Double> closes) {
        if (closes == null || closes.size() < 21) return 0.0;

        double currentMomentum = computeReturn(closes, closes.size() - 1, 20);

        // 금리가 과거 대비 인하되었으면 긍정적
        if (currentMomentum < 0) return 10.0;
        else if (currentMomentum > 0) return -10.0;

        return 0.0; // 동결 유지 시 중립
    }

    /**
     * 최종 점수에 따른 유동성 국면 반환
     */
    private String determineLiquidityRegime(double score) {
        if (score >= 40) return "EXPANDING";
        if (score <= -40) return "CONTRACTING";
        return "NEUTRAL";
    }
}