package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.market.analysis.service.result.AnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.stocklog.modules.common.finance.indicator.MathUtils.computeReturn;
import static com.stocklog.modules.market.analysis.service.analyzer.utils.GetDoublesUtils.getDoubles;

@Component
@Slf4j
public class CrossAssetAnalyzer implements MarketAnalyzer {

    // 각 자산군의 특정 기간(예: 20일) 수익률을 담는 레코드
    public record AssetReturns20(
            double stocks, double yields, double dollar,
            double oil, double gold, double bitcoin,
            double vix, double hySpread
    ) {}

    // 최종 세부 결과와 매크로 내러티브를 담는 레코드
    public record CrossAssetDetails(
            AssetReturns20 returns,
            String macroNarrative
    ) {}

    @Override
    public AnalyzerScore analyze(Map<IndexType,MarketChartData> data) {
        // 1. 주요 자산군 데이터 추출 및 수익률 계산 (20일 모멘텀 기준)
        double stocks = getReturn(data, IndexType.SP500);
        double yields = getReturn(data, IndexType.US_TREASURY_10Y);
        double dollar = getReturn(data, IndexType.DOLLAR_INDEX);
        double oil = getReturn(data, IndexType.WTI_CRUDE_OIL);
        double gold = getReturn(data, IndexType.PHLX_GOLD_SILVER);
        double bitcoin = getReturn(data, IndexType.BITCOIN);
        double vix = getReturn(data, IndexType.VIX);
        double hySpread = getReturn(data, IndexType.HIGH_YIELD_SPREAD);

        AssetReturns20 returns = new AssetReturns20(stocks, yields, dollar, oil, gold, bitcoin, vix, hySpread);

        // 2. 자산군 상관관계를 통한 매크로 환경 판별
        String narrative = determineNarrative(returns);

        // 3. 자산간 다이버전스 및 조합을 고려한 종합 점수 산출 (-100 ~ +100)
        double score = calculateScore(returns);
        ScoreCategory category = AnalyzerScore.categorize(score);
        CrossAssetDetails details = new CrossAssetDetails(returns, narrative);

        return new AnalyzerScore("CrossAsset", score, category, details);
    }

    /**
     * 자산군들의 동시 움직임을 파악하여 현재 시장의 매크로 내러티브 도출
     */
    private String determineNarrative(AssetReturns20 r) {
        boolean isStocksUp = r.stocks() > 0;
        boolean isYieldsUp = r.yields() > 0;
        boolean isDollarUp = r.dollar() > 0;
        boolean isOilUp = r.oil() > 0;
        boolean isGoldUp = r.gold() > 0;
        boolean isVixUp = r.vix() > 0;
        boolean isHySpreadUp = r.hySpread() > 0;

        // 문서 예시 1: 주가상승 + 금리상승 + 달러상승 + 유가상승 (경기 과열 및 리플레이션)
        if (isStocksUp && isYieldsUp && isDollarUp && isOilUp) {
            return "REFLATION_GROWTH";
        }
        // 문서 예시 2: 주가하락 + VIX급등 + 신용스프레드확대 + 달러상승 + 금상승 (극단적 위험 회피 및 패닉)
        if (!isStocksUp && isVixUp && isHySpreadUp && isDollarUp && isGoldUp) {
            return "PANIC_RISK_OFF";
        }
        // 골디락스 / 유동성 랠리: 주가상승 + 금리안정(하락) + 달러약세
        if (isStocksUp && !isYieldsUp && !isDollarUp) {
            return "GOLDILOCKS_LIQUIDITY";
        }
        // 스태그플레이션 우려: 주가하락 + 금리상승 + 달러상승 + 유가상승
        if (!isStocksUp && isYieldsUp && isDollarUp && isOilUp) {
            return "STAGFLATION_FEAR";
        }

        return "MIXED_SIGNALS"; // 혼조세
    }

    /**
     * 개별 자산의 방향성 및 조합을 통해 Risk-On / Risk-Off 점수 산출
     */
    private double calculateScore(AssetReturns20 r) {
        double score = 0;

        // 주식 및 비트코인 (위험자산 강세 시 가산점)
        if (r.stocks() > 0) score += 20;
        else score -= 20;

        if (r.bitcoin() > 0) score += 15;
        else score -= 15;

        // VIX, 신용 스프레드, 달러 (안전자산/위험지표 안정 시 가산점, 불안 시 감점)
        if (r.vix() < 0) score += 15;
        else score -= 15;

        if (r.hySpread() < 0) score += 15;
        else score -= 15;

        if (r.dollar() < 0) score += 10;
        else score -= 10;

        // 크로스 애셋 다이버전스 판별 (금리와 주가의 관계)
        if (r.stocks() > 0 && r.yields() < 0) score += 15; // 금리 하락이 주가를 견인하는 유동성 장세
        if (r.stocks() < 0 && r.yields() > 0) score -= 15; // 금리 상승이 밸류에이션을 압박하는 긴축 우려

        // 유가와 주가의 관계 (성장 견인 vs 인플레이션 우려)
        if (r.oil() > 0 && r.stocks() > 0) score += 10;
        if (r.oil() > 0 && r.stocks() < 0) score -= 10;

        return Math.max(-100.0, Math.min(100.0, score));
    }

    private double getReturn(Map<IndexType,MarketChartData> data, IndexType type) {
        if (!data.containsKey(type)) {
            log.warn("[{}] Map에 존재하지 않음", type.getSeriesId());
            return 0.0;
        }
        MarketChartData chartData = data.get(type);
        List<Double> closes = getDoubles(chartData, type);
        if (closes == null) {
            log.warn("[{}] 불러오기 실패", type.getSeriesId());
            return 0.0;
        }
        return computeReturn(closes, closes.size() - 1, 20); // 20일 기준 수익률 연산
    }
    }
