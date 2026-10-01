package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import com.stocklog.modules.stock.application.usecase.GetStockChartUseCase;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.computeReturn;
import static com.stocklog.modules.common.finance.util.MathUtils.highest;

@Component
@RequiredArgsConstructor
public class DivergenceDetector implements MarketAnalyzer {

    private final GetStockChartUseCase chartUseCase;

    /**
     * 다이버전스 상세 결과
     */
    public record DivergenceDetails(
            boolean hasRiskDivergence,
            boolean hasCreditDivergence,
            boolean hasLeadershipDivergence,
            boolean hasBreadthDivergence,
            List<String> warnings
    ) {}

    /**
     * 시장 다이버전스 분석
     *
     * 주요 감지 항목
     *
     * 1. Risk Divergence
     *    S&P 500 상승 + VIX 상승
     *
     * 2. Credit Divergence
     *    S&P 500 상승 + HY Spread 확대
     *
     * 3. Leadership Divergence
     *    Nasdaq 상승 + SOX 상대약세
     *
     * 4. Breadth Divergence
     *    S&P 500 고점 부근 + RSP가 SPY 대비 상대약세
     */
    @Override
    public MarketAnalyzerScore analyze(
            Map<IndexType, MarketChartData> data
    ) {

        List<String> warnings = new ArrayList<>();

        double penaltyScore = 0.0;

        // =========================================================
        // 1. 시장 데이터 조회
        // =========================================================

        List<Double> sp500Closes =
                getDoubles(data, IndexType.SP500);

        List<Double> nasdaqCloses =
                getDoubles(data, IndexType.NASDAQ_100);

        List<Double> vixCloses =
                getDoubles(data, IndexType.VIX);

        List<Double> hySpreadCloses =
                getDoubles(data, IndexType.HIGH_YIELD_SPREAD);

        List<Double> soxCloses =
                getDoubles(data, IndexType.PHLX_SEMI);

        // Breadth proxy
        List<Double> rspCloses =
                getEtfDoubles("RSP");

        // =========================================================
        // 2. 20일 수익률 계산
        // =========================================================

        double sp500Return =
                calculateReturn(sp500Closes);

        double nasdaqReturn =
                calculateReturn(nasdaqCloses);

        double vixReturn =
                calculateReturn(vixCloses);

        double hySpreadReturn =
                calculateReturn(hySpreadCloses);

        double soxReturn =
                calculateReturn(soxCloses);

        double rspReturn =
                calculateReturn(rspCloses);

        // =========================================================
        // 3. Risk Divergence
        //
        // S&P 상승 + VIX 의미 있는 상승
        // =========================================================

        boolean hasRiskDiv =
                sp500Return > 1.0
                        && vixReturn > 10.0;

        if (hasRiskDiv) {

            warnings.add(
                    String.format(
                            "Risk Divergence: S&P500 상승 중 VIX %.1f%% 상승 (변동성 경고)",
                            vixReturn
                    )
            );

            penaltyScore += calculateRiskPenalty(vixReturn);
        }

        // =========================================================
        // 4. Credit Divergence
        //
        // S&P 상승 + HY Spread 확대
        // =========================================================

        boolean hasCreditDiv =
                sp500Return > 1.0
                        && hySpreadReturn > 3.0;

        if (hasCreditDiv) {

            warnings.add(
                    String.format(
                            "Credit Divergence: S&P500 상승 중 HY Spread %.1f%% 확대 (신용 스트레스 경고)",
                            hySpreadReturn
                    )
            );

            penaltyScore += calculateCreditPenalty(
                    hySpreadReturn
            );
        }

        // =========================================================
        // 5. Leadership Divergence
        //
        // Nasdaq 상승 + SOX 상대약세
        // =========================================================

        double leadershipSpread =
                soxReturn - nasdaqReturn;

        boolean hasLeadershipDiv =
                nasdaqReturn > 1.0
                        && leadershipSpread < -2.0;

        if (hasLeadershipDiv) {

            warnings.add(
                    String.format(
                            "Leadership Divergence: Nasdaq 상승 중 SOX 상대약세 (%.1f%%p)",
                            leadershipSpread
                    )
            );

            penaltyScore += calculateLeadershipPenalty(
                    leadershipSpread
            );
        }

        // =========================================================
        // 6. Breadth Divergence
        //
        // S&P 500이 52주 고점 부근인데
        // RSP가 SPY 대비 상대적으로 약한 경우
        // =========================================================

        boolean hasBreadthDiv = false;

        if (hasEnoughData(sp500Closes)
                && hasEnoughData(rspCloses)) {

            double currentSp500 =
                    last(sp500Closes);

            double high52w =
                    highest(
                            sp500Closes,
                            sp500Closes.size() - 1,
                            250
                    );

            boolean isNearHigh =
                    currentSp500 >= high52w * 0.98;

            /**
             * 핵심:
             *
             * 기존:
             * RSP 자체 수익률 < 0
             *
             * 개선:
             * RSP 수익률 - SPY 수익률 < -3%
             *
             * 시장이 상승하더라도 RSP가 SPY보다
             * 크게 뒤처지면 Breadth Divergence로 판단.
             */
            double breadthSpread =
                    rspReturn - sp500Return;

            boolean hasWeakBreadth =
                    breadthSpread < -3.0;

            if (isNearHigh && hasWeakBreadth) {

                hasBreadthDiv = true;

                warnings.add(
                        String.format(
                                "Breadth Divergence: S&P500 고점 부근에서 동일가중 지수 상대약세 (%.1f%%p)",
                                breadthSpread
                        )
                );

                penaltyScore +=
                        calculateBreadthPenalty(
                                breadthSpread
                        );
            }
        }

        // =========================================================
        // 7. 최종 점수
        // =========================================================

        double finalScore =
                Math.max(
                        -100.0,
                        penaltyScore
                );

        ScoreCategory category =
                MarketAnalyzerScore.categorize(
                        finalScore
                );

        DivergenceDetails details =
                new DivergenceDetails(
                        hasRiskDiv,
                        hasCreditDiv,
                        hasLeadershipDiv,
                        hasBreadthDiv,
                        List.copyOf(warnings)
                );

        return new MarketAnalyzerScore(
                "DivergenceWarning",
                finalScore,
                category,
                details
        );
    }

    // =============================================================
    // Penalty Functions
    // =============================================================

    /**
     * Risk Divergence 패널티
     *
     * VIX 상승률이 클수록 패널티 증가.
     */
    private double calculateRiskPenalty(
            double vixReturn
    ) {

        if (vixReturn >= 25.0) {
            return -25.0;
        }

        if (vixReturn >= 15.0) {
            return -20.0;
        }

        return -15.0;
    }

    /**
     * Credit Divergence 패널티
     *
     * HY Spread 확대가 클수록 패널티 증가.
     */
    private double calculateCreditPenalty(
            double hySpreadReturn
    ) {

        if (hySpreadReturn >= 10.0) {
            return -30.0;
        }

        if (hySpreadReturn >= 5.0) {
            return -25.0;
        }

        return -20.0;
    }

    /**
     * Leadership Divergence 패널티
     */
    private double calculateLeadershipPenalty(
            double leadershipSpread
    ) {

        if (leadershipSpread <= -5.0) {
            return -20.0;
        }

        if (leadershipSpread <= -3.0) {
            return -15.0;
        }

        return -10.0;
    }

    /**
     * Breadth Divergence 패널티
     *
     * RSP - SPY의 상대수익률 괴리가 클수록
     * 패널티 증가.
     */
    private double calculateBreadthPenalty(
            double breadthSpread
    ) {

        if (breadthSpread <= -5.0) {
            return -25.0;
        }

        if (breadthSpread <= -4.0) {
            return -20.0;
        }

        if (breadthSpread <= -3.0) {
            return -15.0;
        }

        return 0.0;
    }

    // =============================================================
    // Utility
    // =============================================================

    /**
     * 20일 수익률 계산
     */
    private double calculateReturn(
            List<Double> closes
    ) {

        if (!hasEnoughReturnData(closes)) {
            return 0.0;
        }

        return computeReturn(
                closes,
                closes.size() - 1,
                20
        );
    }

    /**
     * IndexType 데이터 조회
     *
     * 데이터가 없는 경우 null 반환.
     */
    private List<Double> getDoubles(
            Map<IndexType, MarketChartData> data,
            IndexType indexType
    ) {

        MarketChartData chartData =
                data.get(indexType);

        if (chartData == null
                || chartData.chartDataList() == null) {

            return List.of();
        }

        return chartData
                .chartDataList()
                .stream()
                .map(indexChartDto ->
                        indexChartDto.close().doubleValue()
                )
                .toList();
    }

    /**
     * ETF 데이터 조회
     */
    private List<Double> getEtfDoubles(
            String code
    ) {

        return chartUseCase
                .getStockChartDtoList(code)
                .stream()
                .map(StockChartDto::closeDoubleValue)
                .toList();
    }

    /**
     * 수익률 계산에 필요한 최소 데이터
     */
    private boolean hasEnoughReturnData(
            List<Double> closes
    ) {

        return closes != null
                && closes.size() >= 21;
    }

    /**
     * 60일 Breadth 계산에 필요한 최소 데이터
     */
    private boolean hasEnoughData(
            List<Double> closes
    ) {

        return closes != null
                && closes.size() >= 61;
    }

    /**
     * 마지막 값
     */
    private double last(
            List<Double> values
    ) {

        return values.get(
                values.size() - 1
        );
    }
}