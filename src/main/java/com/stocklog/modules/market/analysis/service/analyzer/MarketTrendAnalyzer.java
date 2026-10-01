package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.common.finance.util.MathUtils;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.computeReturn;
import static com.stocklog.modules.common.finance.indicator.trend.TrendIndicators.calcDistance;
import static com.stocklog.modules.common.finance.util.MathUtils.*;
import static com.stocklog.modules.market.analysis.service.analyzer.utils.GetDoublesUtils.getDoubles;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarketTrendAnalyzer implements MarketAnalyzer {

    // 미국 시장 전체 추세를 대변하는 3대 지수 목록
    private static final List<IndexType> ANALYSIS_INDEX = List.of(IndexType.SP500, IndexType.NASDAQ_100, IndexType.DOW_JONES);
    private static final int TRADING_DAYS_52W = 250;

    private record MovingAverage(double ma20, double ma50, double ma200, double disparity20, double disparity50, double disparity200) {}
    private record Slope(double ma20Slope, double ma50Slope, double ma200Slope) {}
    private record Returns(double return20, double return60, double return120, double return200) {}
    private record Drawdown(double drawdown, double high52wProximity) {}

    // 개별 지수의 추세 결과
    public record IndexTrendDetails(MovingAverage ma, Slope slope, Returns returns, Drawdown dd, double score) {}
    // 3대 지수를 모두 담는 최종 DTO
    public record TrendDetails(Map<IndexType, IndexTrendDetails> indexDetails) {}

    @Override
    public MarketAnalyzerScore analyze(Map<IndexType, MarketChartData> data) {
        Map<IndexType, IndexTrendDetails> detailsMap = new EnumMap<>(IndexType.class);
        double totalScore = 0;
        int validCount = 0;

        for (IndexType indexType : ANALYSIS_INDEX) {
            List<Double> closes = getDoubles(data, indexType,TRADING_DAYS_52W);

            MovingAverage ma = getMovingAverage(closes);
            Slope slope = getSlope(closes);
            Returns returns = getReturn(closes);
            Drawdown dd = getDrawdown(closes);
            double currentPrice = closes.get(closes.size() - 1);

            double score = calculateNormalizedScore(currentPrice, ma, slope, returns, dd);

            detailsMap.put(indexType, new IndexTrendDetails(ma, slope, returns, dd, score));
            totalScore += score;
            validCount++;
        }

        // 3대 지수 점수의 평균으로 최종 시장 추세 점수 산출
        double finalScore = (validCount > 0) ? (totalScore / validCount) : 0.0;

        ScoreCategory category = MarketAnalyzerScore.categorize(finalScore);
        TrendDetails details = new TrendDetails(detailsMap);

        return new MarketAnalyzerScore("MarketTrend", finalScore, category, details);
    }

    private double calculateNormalizedScore(double price, MovingAverage ma, Slope slope, Returns returns, Drawdown dd) {
        double score = 0;

        // [팩터 1] 이동평균 배열 (총 30점)
        // (현재 주가가 단기 이평선 위에 있는지가 이미 포함되어 있으므로 배점 유지)
        if (price > ma.ma20() && ma.ma20() > ma.ma50() && ma.ma50() > ma.ma200()) score += 30;
        else if (price > ma.ma50() && ma.ma50() > ma.ma200()) score += 15;
        else if (price < ma.ma20() && ma.ma20() < ma.ma50() && ma.ma50() < ma.ma200()) score -= 30;
        else if (price < ma.ma50() && ma.ma50() < ma.ma200()) score -= 15;

        // [팩터 2] 이동평균선 기울기 (총 40점)
        // 변경: 200일선의 기울기 비중을 대폭 낮추고, 20일선(단기 추세) 비중을 높임
        score += (slope.ma200Slope() > 0) ? 5 : -5;   // 20 -> 5
        score += (slope.ma50Slope() > 0)  ? 15 : -15; // 10 -> 15
        score += (slope.ma20Slope() > 0)  ? 20 : -20; // 10 -> 20

        // [팩터 3] 기간별 수익률 (총 15점)
        // 변경: 장기(200일)보다 단기(20일, 60일) 수익률에 가중치 부여
        score += (returns.return200() > 0) ? 2 : -2; // 5 -> 2
        score += (returns.return120() > 0) ? 3 : -3; // 4 -> 3
        score += (returns.return60() > 0)  ? 4 : -4; // 3 -> 4
        score += (returns.return20() > 0)  ? 6 : -6; // 3 -> 6

        // [팩터 4] 위험도 및 돌파 가능성 (총 15점)
        // (52주 신고가 근접도는 훌륭한 최근 강세 지표이므로 그대로 유지)
        if (dd.high52wProximity() >= 95.0) score += 15;
        else if (dd.high52wProximity() >= 90.0) score += 7;

        if (dd.drawdown() <= -20.0) score -= 15;
        else if (dd.drawdown() <= -10.0) score -= 7;

        return Math.max(-100.0, Math.min(100.0, score));
    }

    private MovingAverage getMovingAverage(List<Double> closes) {
        int lastIdx = closes.size() - 1;
        double currentPrice = closes.get(lastIdx);
        double ma20 = MathUtils.sma(closes, lastIdx, 20);
        double ma50 = MathUtils.sma(closes, lastIdx, 50);
        double ma200 = MathUtils.sma(closes, lastIdx, 200);
        return new MovingAverage(ma20, ma50, ma200, calcDistance(currentPrice, ma20), calcDistance(currentPrice, ma50), calcDistance(currentPrice, ma200));
    }

    private Slope getSlope(List<Double> prices) {
        int today = prices.size() - 1;
        double ma20Slope = ((sma(prices, today, 20) - sma(prices, today - 3, 20)) / sma(prices, today - 3, 20)) * 100.0;
        double ma50Slope = ((sma(prices, today, 50) - sma(prices, today - 5, 50)) / sma(prices, today - 5, 50)) * 100.0;
        double ma200Slope = ((sma(prices, today, 200) - sma(prices, today - 10, 200)) / sma(prices, today - 10, 200)) * 100.0;
        return new Slope(ma20Slope, ma50Slope, ma200Slope);
    }

    private Returns getReturn(List<Double> closes) {
        int lastIdx = closes.size() - 1;
        return new Returns(
                computeReturn(closes, lastIdx, 20),
                computeReturn(closes, lastIdx, 60),
                computeReturn(closes, lastIdx, 120),
                computeReturn(closes, lastIdx, 200)
        );
    }

    private Drawdown getDrawdown(List<Double> closes) {
        double currentPrice = closes.get(closes.size() - 1);
        double high52w = highest(closes, closes.size() - 1, TRADING_DAYS_52W);
        return new Drawdown(((currentPrice - high52w) / high52w) * 100.0, (currentPrice / high52w) * 100.0);
    }
}