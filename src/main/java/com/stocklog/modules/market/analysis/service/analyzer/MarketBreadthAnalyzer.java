package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexEtf;
import com.stocklog.modules.market.domain.type.IndexType;
import com.stocklog.modules.stock.application.usecase.GetStockChartUseCase;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.computeReturn;

@Component
@RequiredArgsConstructor
public class MarketBreadthAnalyzer implements MarketAnalyzer {

    private final GetStockChartUseCase chartUseCase;

    /** 시장 확산도 분석에 사용되는 기간별 상대수익률 스프레드 */
    public record ReturnSpreads(
            double spread20D,
            double spread60D
    ) {}

    /** Breadth 분석의 세부 결과 */
    public record BreadthDetails(
            ReturnSpreads sp500Spread,
            ReturnSpreads smallCapSpread,
            ReturnSpreads nasdaqSpread
    ) {}

    /** 시장 Breadth 분석 */
    public MarketAnalyzerScore analyze(Map<IndexType, MarketChartData> data) {

        List<Double> spyCloses = getEtfDoubles(IndexEtf.SPY.getCode());
        List<Double> rspCloses = getEtfDoubles(IndexEtf.RSP.getCode());

        List<Double> qqqCloses = getEtfDoubles(IndexEtf.QQQ.getCode());
        List<Double> qqewCloses = getEtfDoubles(IndexEtf.QQEW.getCode());

        List<Double> iwmCloses = getEtfDoubles(IndexEtf.IWM.getCode());

        ReturnSpreads sp500Spread =
                getSp500BreadthSpread(spyCloses, rspCloses);

        ReturnSpreads smallCapSpread =
                getSmallCapSpread(spyCloses, iwmCloses);

        ReturnSpreads nasdaqSpread =
                getNasdaqBreadthSpread(qqqCloses, qqewCloses);

        double score = calculateNormalizedScore(
                sp500Spread,
                smallCapSpread,
                nasdaqSpread
        );

        ScoreCategory category = MarketAnalyzerScore.categorize(score);

        BreadthDetails details = new BreadthDetails(
                sp500Spread,
                smallCapSpread,
                nasdaqSpread
        );

        return new MarketAnalyzerScore(
                "MarketBreadth",
                score,
                category,
                details
        );
    }

    /** 최종 Breadth 점수 계산 */
    private double calculateNormalizedScore(
            ReturnSpreads sp500Spread,
            ReturnSpreads smallCapSpread,
            ReturnSpreads nasdaqSpread
    ) {

        double score = 0.0;

        // S&P 500 내부 확산 : 최대 ±40

        score += evaluateSpread(
                sp500Spread.spread20D(),
                20.0
        );

        score += evaluateSpread(
                sp500Spread.spread60D(),
                20.0
        );

        // Small Cap 참여도 : 최대 ±30

        score += evaluateSpread(
                smallCapSpread.spread20D(),
                15.0
        );

        score += evaluateSpread(
                smallCapSpread.spread60D(),
                15.0
        );

        // Nasdaq 내부 확산 : 최대 ±30

        score += evaluateSpread(
                nasdaqSpread.spread20D(),
                15.0
        );

        score += evaluateSpread(
                nasdaqSpread.spread60D(),
                15.0
        );

        return clamp(score, -100.0, 100.0);
    }

    /** 상대수익률 스프레드를 연속형 점수로 변환 */
    private double evaluateSpread(
            double spread,
            double maxScore
    ) {

        double normalized = Math.tanh(spread / 4.0);

        return normalized * maxScore;
    }

    /** S&P 500 내부 확산 */
    private ReturnSpreads getSp500BreadthSpread(
            List<Double> spyCloses,
            List<Double> rspCloses
    ) {

        if (!hasEnoughData(spyCloses)
                || !hasEnoughData(rspCloses)) {

            return new ReturnSpreads(0.0, 0.0);
        }

        return calculateReturnSpread(
                spyCloses,
                rspCloses
        );
    }

    /** Small Cap 참여도 */
    private ReturnSpreads getSmallCapSpread(
            List<Double> spyCloses,
            List<Double> iwmCloses
    ) {

        if (!hasEnoughData(spyCloses)
                || !hasEnoughData(iwmCloses)) {

            return new ReturnSpreads(0.0, 0.0);
        }

        return calculateReturnSpread(
                spyCloses,
                iwmCloses
        );
    }

    /** Nasdaq 100 내부 확산 */
    private ReturnSpreads getNasdaqBreadthSpread(
            List<Double> qqqCloses,
            List<Double> qqewCloses
    ) {

        if (!hasEnoughData(qqqCloses)
                || !hasEnoughData(qqewCloses)) {

            return new ReturnSpreads(0.0, 0.0);
        }

        return calculateReturnSpread(
                qqqCloses,
                qqewCloses
        );
    }

    /** 두 ETF의 상대수익률 스프레드 계산 */
    private ReturnSpreads calculateReturnSpread(
            List<Double> baseCloses,
            List<Double> targetCloses
    ) {

        int baseLastIdx = baseCloses.size() - 1;
        int targetLastIdx = targetCloses.size() - 1;

        double baseReturn20 =
                computeReturn(
                        baseCloses,
                        baseLastIdx,
                        20
                );

        double targetReturn20 =
                computeReturn(
                        targetCloses,
                        targetLastIdx,
                        20
                );

        double baseReturn60 =
                computeReturn(
                        baseCloses,
                        baseLastIdx,
                        60
                );

        double targetReturn60 =
                computeReturn(
                        targetCloses,
                        targetLastIdx,
                        60
                );

        return new ReturnSpreads(
                targetReturn20 - baseReturn20,
                targetReturn60 - baseReturn60
        );
    }

    /** 최소 61개 데이터 필요 60일 수익률 계산을 위해 사용 */
    private boolean hasEnoughData(List<Double> closes) {
        return closes != null && closes.size() >= 61;
    }

    /** 범위 제한 */
    private double clamp(
            double value,
            double min,
            double max
    ) {

        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    /** ETF 차트 데이터 조회 */
    private List<Double> getEtfDoubles(String code) {

        return chartUseCase
                .getStockChartDtoList(code)   // 최신순(내림차순)으로 온다
                .stream()
                // computeReturn은 '마지막 원소 = 오늘'을 가정하므로 날짜 오름차순으로 뒤집는다
                .filter(c -> c != null && c.date() != null && c.close() != null)
                .sorted(Comparator.comparing(StockChartDto::date))
                .map(StockChartDto::closeDoubleValue)
                .toList();
    }
}