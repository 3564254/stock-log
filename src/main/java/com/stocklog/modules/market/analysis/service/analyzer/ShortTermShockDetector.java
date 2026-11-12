package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexEtf;
import com.stocklog.modules.market.domain.type.IndexType;
import com.stocklog.modules.stock.application.usecase.GetStockChartUseCase;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.computeReturn;
import static com.stocklog.modules.market.analysis.service.analyzer.utils.GetDoublesUtils.getDoubles;

/** 단기 충격 감지기 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortTermShockDetector implements MarketAnalyzer {

    private static final int DAYS = 5;
    private static final double SPX_DROP = -3.0;
    private static final double NDX_DROP = -4.0;
    private static final double VIX_JUMP = 30.0;
    private static final double VIX_HIGH_LEVEL = 25.0;
    private static final double VIX_HIGH_JUMP = 15.0;

    private final GetStockChartUseCase chartUseCase;

    public record ShockDetails(
            double sp500Return5D,
            double nasdaqReturn5D,
            double vixChange5D,
            double vixLevel,
            boolean sharpDrop,
            boolean volatilitySpike,
            boolean triggered,
            List<String> warnings
    ) {}

    @Override
    public MarketAnalyzerScore analyze(Map<IndexType, MarketChartData> data) {
        List<Double> spx = etfOrIndex(IndexEtf.SPY.getCode(), data, IndexType.SP500);
        List<Double> ndx = etfOrIndex(IndexEtf.QQQ.getCode(), data, IndexType.NASDAQ_100);
        List<Double> vix = getDoubles(data, IndexType.VIX);

        double spx5 = return5(spx);
        double ndx5 = return5(ndx);
        double vix5 = return5(vix);
        double vixLevel = vix.isEmpty() ? 0.0 : vix.get(vix.size() - 1);

        boolean sharpDrop = spx5 <= SPX_DROP || ndx5 <= NDX_DROP;
        boolean volSpike = vix5 >= VIX_JUMP || (vixLevel >= VIX_HIGH_LEVEL && vix5 >= VIX_HIGH_JUMP);

        List<String> warnings = new ArrayList<>();
        if (sharpDrop) {
            warnings.add(String.format("Short-term Selloff (5일 급락: S&P 500 %.1f%%, 나스닥 100 %.1f%%)", spx5, ndx5));
        }
        if (volSpike) {
            warnings.add(String.format("Volatility Spike (VIX %.1f, 5일 새 %+.0f%%)", vixLevel, vix5));
        }

        boolean triggered = sharpDrop || volSpike;
        // 점수는 참고용: 급락과 변동성 급등이 겹치면 더 낮게. 가중합에는 쓰지 않는다
        double score = (sharpDrop ? -50 : 0) + (volSpike ? -30 : 0);
        ScoreCategory category = MarketAnalyzerScore.categorize(score);

        return new MarketAnalyzerScore("ShortTermShock", score, category,
                new ShockDetails(spx5, ndx5, vix5, vixLevel, sharpDrop, volSpike, triggered, List.copyOf(warnings)));
    }

    private static double return5(List<Double> closes) {
        if (closes == null || closes.size() <= DAYS) return 0.0;
        return computeReturn(closes, closes.size() - 1, DAYS);
    }

    /** 증권사 ETF 일봉을 먼저, 실패하거나 부족하면 FRED 지수 */
    private List<Double> etfOrIndex(String etfCode, Map<IndexType, MarketChartData> data, IndexType fallback) {
        try {
            List<Double> etf = chartUseCase.getStockChartDtoList(etfCode).stream()
                    .filter(c -> c != null && c.date() != null && c.close() != null)
                    .sorted(Comparator.comparing(StockChartDto::date))
                    .map(StockChartDto::closeDoubleValue)
                    .toList();
            if (etf.size() > DAYS) return etf;
        } catch (Exception e) {
            log.warn("[단기 충격] {} 일봉 조회 실패, FRED {}로 대신합니다: {}", etfCode, fallback.getSeriesId(), e.getMessage());
        }
        return getDoubles(data, fallback);
    }
}
