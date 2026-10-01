package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.common.finance.model.MacdResult;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.*;
import static com.stocklog.modules.market.analysis.service.analyzer.utils.GetDoublesUtils.getDoubles;

@Component
@RequiredArgsConstructor
public class MarketMomentumAnalyzer implements MarketAnalyzer {

    // 미국 시장 전체 모멘텀을 대변하기 위한 3대 지수 종합 평가
    private static final List<IndexType> ANALYSIS_INDEX = List.of(IndexType.SP500, IndexType.NASDAQ_100, IndexType.DOW_JONES);

    private record Returns(double return1D, double return5D, double return20D, double return60D, double return120D) {}
    private record Roc(double roc10, double roc20, double roc60) {}
    private record Rsi(double rsi9, double rsi14) {}
    private record Macd(MacdResult macdResult) {}
    private record MomentumAcceleration(double accel5D, double accel20D) {}

    // 개별 지수의 모멘텀 결과
    public record IndexMomentumDetails(Returns returns, Roc roc, Rsi rsi, Macd macd, MomentumAcceleration accel, double score) {}
    // 3대 지수를 모두 담는 최종 DTO
    public record MomentumDetails(Map<IndexType, IndexMomentumDetails> indexDetails) {}

    @Override
    public MarketAnalyzerScore analyze(Map<IndexType, MarketChartData> data) {
        Map<IndexType, IndexMomentumDetails> detailsMap = new EnumMap<>(IndexType.class);
        double totalScore = 0;
        int validCount = 0;

        for (IndexType indexType : ANALYSIS_INDEX) {
            List<Double> closes = getDoubles(data, indexType);
            // 장기 지표 계산을 위해 충분한 데이터가 존재하는지 검증 (최소 120일)
            if (closes == null || closes.size() < 120) continue;

            Returns returns = getReturn(closes);
            Roc roc = getRoc(closes);
            Rsi rsi = getRsi(closes);
            Macd macd = getMacd(closes);
            MomentumAcceleration accel = getMomentumAcceleration(closes);

            double score = calculateNormalizedScore(returns, roc, rsi, macd, accel);
            detailsMap.put(indexType, new IndexMomentumDetails(returns, roc, rsi, macd, accel, score));
            totalScore += score;
            validCount++;
        }

        // 3대 지수 점수 평균화 처리
        double finalScore = (validCount > 0) ? (totalScore / validCount) : 0.0;
        ScoreCategory category = MarketAnalyzerScore.categorize(finalScore);
        MomentumDetails details = new MomentumDetails(detailsMap);

        return new MarketAnalyzerScore("MarketMomentum", finalScore, category, details);
    }

    private double calculateNormalizedScore(Returns returns, Roc roc, Rsi rsi, Macd macd, MomentumAcceleration accel) {
        double score = 0;

        // [팩터 1] 기간별 수익률 (총 25점)
        // 변경: 5일과 20일 수익률에 가장 큰 가중치 부여 (기존은 60일이 10점)
        score += (returns.return60D() > 0) ? 4 : -4;  // 10 -> 4
        score += (returns.return20D() > 0) ? 8 : -8;  // 7 -> 8
        score += (returns.return5D() > 0)  ? 9 : -9;  // 5 -> 9
        score += (returns.return1D() > 0)  ? 4 : -4;  // 3 -> 4

        // [팩터 2] ROC (총 15점)
        // 변경: 최근 10일의 변화율에 집중 (기존은 모두 5점)
        score += (roc.roc60() > 0) ? 2 : -2;  // 5 -> 2
        score += (roc.roc20() > 0) ? 5 : -5;  // 5 유지
        score += (roc.roc10() > 0) ? 8 : -8;  // 5 -> 8

        // [팩터 3] RSI (총 20점)
        // (단기 민감도가 높은 지표이므로 배점 유지)
        if (rsi.rsi14() > 70) score += 20;
        else if (rsi.rsi14() > 50) score += 10;
        else if (rsi.rsi14() < 30) score -= 20;
        else if (rsi.rsi14() < 50) score -= 10;

        // [팩터 4] MACD (총 20점)
        // 변경: 절대값(0선 돌파)보다 최근 추세 변화를 나타내는 시그널선 돌파에 훨씬 높은 가중치 부여
        double macdValue = macd.macdResult().macd();
        double signalValue = macd.macdResult().signal();
        score += (macdValue > 0) ? 5 : -5;                     // 10 -> 5
        score += (macdValue > signalValue) ? 15 : -15;         // 10 -> 15

        // [팩터 5] 모멘텀 가속도 (총 20점)
        // 변경: 5일 가속도(극초단기 모멘텀)에 더 큰 배점
        score += (accel.accel20D() > 0) ? 5 : -5;   // 10 -> 5
        score += (accel.accel5D() > 0)  ? 15 : -15; // 10 -> 15

        return Math.max(-100.0, Math.min(100.0, score));
    }

    private MomentumAcceleration getMomentumAcceleration(List<Double> closes) {
        int lastIdx = closes.size() - 1;
        double accel5 = computeReturn(closes, lastIdx, 5) - computeReturn(closes, lastIdx - 5, 5);
        double accel20 = computeReturn(closes, lastIdx, 20) - computeReturn(closes, lastIdx - 5, 20);
        return new MomentumAcceleration(accel5, accel20);
    }

    private Returns getReturn(List<Double> closes) {
        int lastIdx = closes.size() - 1;
        return new Returns(
                computeReturn(closes, lastIdx, 1),
                computeReturn(closes, lastIdx, 5),
                computeReturn(closes, lastIdx, 20),
                computeReturn(closes, lastIdx, 60),
                computeReturn(closes, lastIdx, 120)
        );
    }

    private Roc getRoc(List<Double> closes) {
        return new Roc(rateOfChange(closes, 10), rateOfChange(closes, 20), rateOfChange(closes, 60));
    }

    private Rsi getRsi(List<Double> closes) {
        int lastIdx = closes.size() - 1;
        return new Rsi(calcRsiWilder(closes, 9, lastIdx), calcRsiWilder(closes, 14, lastIdx));
    }

    private Macd getMacd(List<Double> closes) {
        return new Macd(calcMacd(closes));
    }
}