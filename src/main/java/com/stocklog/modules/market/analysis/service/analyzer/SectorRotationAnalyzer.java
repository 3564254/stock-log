package com.stocklog.modules.market.analysis.service.analyzer;

import com.stocklog.modules.market.analysis.service.result.MarketAnalyzerScore;
import com.stocklog.modules.market.analysis.service.result.MarketAnalyzer;
import com.stocklog.modules.market.analysis.service.result.ScoreCategory;
import com.stocklog.modules.common.type.Period;
import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.stocklog.modules.common.finance.indicator.trend.MomentumIndicators.computeReturn;
import static com.stocklog.modules.market.analysis.service.analyzer.utils.GetDoublesUtils.getDoubles;

@Component
public class SectorRotationAnalyzer implements MarketAnalyzer {

    // 분석할 대상 섹터 목록: S&P 500(일간 종가)과 같은 기준으로 비교할 수 있는 '일간 가격 지수'만
    private static final List<IndexType> TARGET_SECTORS = List.of(
            IndexType.PHLX_SEMI, IndexType.NASDAQ_AI_BIGDATA, IndexType.NASDAQ_CAPEX_ACHIEVERS,
            IndexType.NASDAQ_CONSUMER_STAPLES, IndexType.NASDAQ_ISE_CYBERSECURITY, IndexType.NASDAQ_BIOTECH,
            IndexType.NASDAQ_CLEAN_ENERGY, IndexType.PHLX_GOLD_SILVER
    );

    // Risk-On 성향의 섹터
    private static final Set<IndexType> RISK_ON_SECTORS = Set.of(
            IndexType.PHLX_SEMI, IndexType.NASDAQ_AI_BIGDATA, IndexType.NASDAQ_CAPEX_ACHIEVERS
    );

    // Risk-Off (방어적) 성향의 섹터
    private static final Set<IndexType> DEFENSIVE_SECTORS = Set.of(
            IndexType.NASDAQ_CONSUMER_STAPLES, IndexType.PHLX_GOLD_SILVER
    );

    // 개별 섹터의 강도 데이터
    public record SectorStrength(IndexType sector, double absoluteReturn, double relativeStrength) {}

    // 최종 세부 결과를 담는 레코드
    public record RotationDetails(
            List<String> leadership,
            List<String> weakness,
            List<SectorStrength> rankings
    ) {}

    @Override
    public MarketAnalyzerScore analyze(Map<IndexType,MarketChartData> data) {
        // 벤치마크 (S&P 500) 수익률 계산 (20일 기준)
        List<Double> sp500Closes = getDoubles(data, IndexType.SP500);
        if (sp500Closes == null || sp500Closes.size() < 21) {
            return new MarketAnalyzerScore("SectorRotation", 0, ScoreCategory.NEUTRAL, null);
        }
        double sp500Return = computeReturn(sp500Closes, sp500Closes.size() - 1, 20);

        // 각 섹터의 절대 수익률 및 상대강도 계산
        List<SectorStrength> sectorStrengths = new ArrayList<>();

        for (IndexType sector : TARGET_SECTORS) {
            if (sector.getFrequency() != Period.DAY) continue;   // 안전장치: 목록에 일간이 아닌 지표가 다시 들어와도 비교하지 않는다
            List<Double> sectorCloses = getDoubles(data, sector);
            if (sectorCloses != null && sectorCloses.size() >= 21) {
                double absReturn = computeReturn(sectorCloses, sectorCloses.size() - 1, 20);
                // 핵심: Sector Return - S&P500 Return = Relative Strength
                double relativeStrength = absReturn - sp500Return;
                sectorStrengths.add(new SectorStrength(sector, absReturn, relativeStrength));
            }
        }

        // 상대강도 내림차순 정렬
        sectorStrengths.sort(Comparator.comparingDouble(SectorStrength::relativeStrength).reversed());

        // Leadership (Top 3) 및 Weakness (Bottom 2) 추출
        List<String> leadership = sectorStrengths.stream()
                .limit(3)
                .map(s -> groom(s.sector().getDisplayName()))
                .collect(Collectors.toList());

        List<String> weakness = sectorStrengths.stream()
                .skip(Math.max(0, sectorStrengths.size() - 2))
                .map(s -> groom(s.sector().getDisplayName()))
                .collect(Collectors.toList());

        // 섹터 로테이션 전체 점수 산출 (-100 ~ +100)
        double score = calculateRotationScore(sectorStrengths);
        ScoreCategory category = MarketAnalyzerScore.categorize(score);

        RotationDetails details = new RotationDetails(leadership, weakness, sectorStrengths);

        return new MarketAnalyzerScore("SectorRotation", score, category, details);
    }

    /** 주도 섹터 성향으로 시장 국면 점수를 계산한다 */
    private double calculateRotationScore(List<SectorStrength> strengths) {
        double score = 0;

        for (SectorStrength s : strengths) {
            // 상대강도가 양수(아웃퍼폼)인지 음수(언더퍼폼)인지에 따라 배점
            if (RISK_ON_SECTORS.contains(s.sector())) {
                // 기술/성장/경기민감주가 시장을 주도하면 +점수 (최대 가중치 부여)
                score += (s.relativeStrength() * 3.0);
            } else if (DEFENSIVE_SECTORS.contains(s.sector())) {
                // 방어주가 시장을 주도하면 -점수 (경계 신호)
                score -= (s.relativeStrength() * 4.0);
            }
        }

        // -100 ~ 100 범위로 정규화
        return Math.max(-100.0, Math.min(100.0, score));
    }

    private String groom(String s) {
        if (!(s.contains("(") || s.contains(")"))) return s;
        int start = s.indexOf("(");

        String newStr = s.substring(0, start - 1);
        return newStr.strip();
    }

}