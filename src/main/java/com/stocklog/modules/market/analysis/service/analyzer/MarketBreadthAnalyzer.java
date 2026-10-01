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

import java.util.List;
import java.util.Map;
import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.computeReturn;

@Component
@RequiredArgsConstructor
public class MarketBreadthAnalyzer implements MarketAnalyzer {

    private final GetStockChartUseCase chartUseCase;

    /**
     * 시장 확산도 분석에 사용되는 기간별 상대수익률 스프레드
     *
     * spread = 비교 대상 수익률 - 기준 지수 수익률
     *
     * 예:
     * RSP - SPY
     * IWM - SPY
     * QQEW - QQQ
     */
    public record ReturnSpreads(
            double spread20D,
            double spread60D
    ) {}

    /**
     * Breadth 분석의 세부 결과
     */
    public record BreadthDetails(
            ReturnSpreads sp500Spread,
            ReturnSpreads smallCapSpread,
            ReturnSpreads nasdaqSpread
    ) {}

    /**
     * 시장 Breadth 분석
     *
     * 분석 구조:
     *
     * 1. S&P 500 내부 확산
     *    RSP vs SPY
     *
     * 2. 중소형주 참여도
     *    IWM vs SPY
     *
     * 3. Nasdaq 100 내부 확산
     *    QQEW vs QQQ
     *
     * 최종 점수 범위: -100 ~ +100
     */
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

    /**
     * 최종 Breadth 점수 계산
     *
     * 가중치
     *
     * S&P 500 내부 확산 : 40점
     *   - 20D : 20
     *   - 60D : 20
     *
     * Small Cap 참여도 : 30점
     *   - 20D : 15
     *   - 60D : 15
     *
     * Nasdaq 내부 확산 : 30점
     *   - 20D : 15
     *   - 60D : 15
     *
     * 총점: -100 ~ +100
     */
    private double calculateNormalizedScore(
            ReturnSpreads sp500Spread,
            ReturnSpreads smallCapSpread,
            ReturnSpreads nasdaqSpread
    ) {

        double score = 0.0;

        // =========================================================
        // 1. S&P 500 내부 확산 : 최대 ±40
        // =========================================================

        score += evaluateSpread(
                sp500Spread.spread20D(),
                20.0
        );

        score += evaluateSpread(
                sp500Spread.spread60D(),
                20.0
        );

        // =========================================================
        // 2. Small Cap 참여도 : 최대 ±30
        // =========================================================

        score += evaluateSpread(
                smallCapSpread.spread20D(),
                15.0
        );

        score += evaluateSpread(
                smallCapSpread.spread60D(),
                15.0
        );

        // =========================================================
        // 3. Nasdaq 내부 확산 : 최대 ±30
        // =========================================================

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

    /**
     * 상대수익률 스프레드를 연속형 점수로 변환
     *
     * 기존 방식:
     *
     * +2% 이상  → +Max
     * 0~2%      → +Max/2
     * -2~-0%    → -Max/2
     * -2% 이하  → -Max
     *
     * 문제:
     * -2.01%와 -10%가 동일한 점수가 됨.
     *
     * 개선:
     * tanh를 이용해 스프레드가 커질수록 점수가
     * 자연스럽게 포화되도록 구성.
     *
     * 기준:
     * 약 ±4% 정도의 스프레드에서 강한 확산/축소로 수렴.
     */
    private double evaluateSpread(
            double spread,
            double maxScore
    ) {

        double normalized = Math.tanh(spread / 4.0);

        return normalized * maxScore;
    }

    /**
     * S&P 500 내부 확산
     *
     * RSP - SPY
     *
     * RSP가 SPY보다 강하면:
     * → 상승 참여 종목이 넓어지는 것으로 해석
     *
     * RSP가 SPY보다 약하면:
     * → 대형주 쏠림으로 해석
     */
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

    /**
     * Small Cap 참여도
     *
     * IWM - SPY
     *
     * IWM이 SPY보다 강하면:
     * → 시장 상승의 저변이 넓어지는 것으로 해석
     *
     * IWM이 SPY보다 약하면:
     * → 대형주 중심 장세로 해석
     */
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

    /**
     * Nasdaq 100 내부 확산
     *
     * QQEW - QQQ
     *
     * QQEW가 QQQ보다 강하면:
     * → Nasdaq 내부 종목 확산
     *
     * QQEW가 QQQ보다 약하면:
     * → 소수 대형 기술주 중심 상승
     */
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

    /**
     * 두 ETF의 상대수익률 스프레드 계산
     *
     * spread = targetReturn - baseReturn
     */
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

    /**
     * 최소 61개 데이터 필요
     *
     * 60일 수익률 계산을 위해 사용.
     */
    private boolean hasEnoughData(List<Double> closes) {
        return closes != null && closes.size() >= 61;
    }

    /**
     * 범위 제한
     */
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

    /**
     * ETF 차트 데이터 조회
     */
    private List<Double> getEtfDoubles(String code) {

        return chartUseCase
                .getStockChartDtoList(code)
                .stream()
                .map(StockChartDto::closeDoubleValue)
                .toList();
    }
}