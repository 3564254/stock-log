package com.stocklog.domain.marketdata.analysis;

import com.stocklog.domain.marketdata.index.dto.IndexResponse;
import com.stocklog.domain.marketdata.index.dto.MarketDashboardResponse;
import com.stocklog.domain.marketdata.index.dto.vo.IndexChartData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MarketTrendAnalyzer {


    public MarketTrendResult analyze(MarketDashboardResponse dashboard) {
        List<String> signals = new ArrayList<>();

        double totalScore = 0.0;
        double totalWeight = 0.0;

        // 1. 투자 심리 부문 (가중치 20%)
        if (isValid(dashboard.vix())) {
            totalScore += analyzeSentiment(dashboard.vix(), signals) * 0.20;
            totalWeight += 0.20;
        }

        // 2. 시장 추세 부문 (가중치 30%)
        if (isValid(dashboard.sp500())) {
            totalScore += analyzeTrend(dashboard.sp500(), dashboard.phlxSemi(), signals) * 0.30;
            totalWeight += 0.30;
        }

        // 3. 유동성 및 환율 부문 (가중치 25%)
        if (isValid(dashboard.dxy()) || isValid(dashboard.usdKrw())) {
            totalScore += analyzeLiquidity(dashboard.dxy(), dashboard.usdKrw(), signals) * 0.25;
            totalWeight += 0.25;
        }

        // 4. 거시 경제 (금리 및 물가) 부문 (가중치 25%)
        if (isValid(dashboard.us10y())) {
            totalScore += analyzeMacro(dashboard.us10y(), dashboard.us2y(), dashboard.wti(), signals) * 0.25;
            totalWeight += 0.25;
        }

        // 데이터가 아예 없는 최악의 경우 방어
        if (totalWeight == 0.0) {
            signals.add("⚠️ 시장 데이터를 불러올 수 없어 분석이 보류되었습니다.");
            return new MarketTrendResult(50, "분석 보류", signals);
        }

        // 누락된 데이터가 있어도 남은 가중치 비율로 환산하여 100점 만점으로 스케일링
        int finalScore = (int) Math.round(totalScore / totalWeight);
        finalScore = Math.max(0, Math.min(100, finalScore));

        return new MarketTrendResult(finalScore, determineRegime(finalScore), signals);
    }

    // ==========================================
    // 팩터별 분석 로직 (리턴값: 0~100점의 위험도 점수)
    // ==========================================

    private double analyzeSentiment(IndexResponse vix, List<String> signals) {
        double currentVix = getLastValue(vix).doubleValue();
        if (currentVix < 15.0) {
            signals.add("🟢 [심리] VIX 15 하회: 시장 평온 및 탐욕 구간 (Risk-On)");
            return 20.0; // 매우 안전
        } else if (currentVix < 25.0) {
            signals.add("🟡 [심리] VIX 정상 범위: 변동성 안정화 구간");
            return 50.0; // 중립
        } else if (currentVix < 35.0) {
            signals.add("🟠 [심리] VIX 25 상회: 변동성 확대 및 경계 심리 발동");
            return 80.0; // 위험
        } else {
            signals.add("🔴 [심리] VIX 35 상회: 극단적 패닉 및 투매 진행 중");
            return 100.0; // 극단적 위험
        }
    }

    private double analyzeTrend(IndexResponse sp500, IndexResponse phlx, List<String> signals) {
        double score = 50.0;

        BigDecimal currentSp500 = getLastValue(sp500);
        BigDecimal ma50 = calculateSMA(sp500, 50);
        BigDecimal ma200 = calculateSMA(sp500, 200);

        if (ma200 != null && ma50 != null) {
            if (currentSp500.compareTo(ma200) < 0) {
                signals.add("🔴 [추세] S&P 500 장기(200일) 이평선 붕괴: 약세장(Bear Market) 진입");
                score = 90.0;
            } else if (currentSp500.compareTo(ma50) < 0) {
                signals.add("🟠 [추세] S&P 500 장기 상승 유지 중 단기(50일) 조정 구간");
                score = 65.0;
            } else {
                signals.add("🟢 [추세] S&P 500 중장기 정배열 상승 추세");
                score = 25.0;
            }
        }

        // 반도체 지수 보조 지표 반영 (최대 10점 조정)
        if (isValid(phlx)) {
            BigDecimal currentPhlx = getLastValue(phlx);
            BigDecimal phlxMa50 = calculateSMA(phlx, 50);
            if (phlxMa50 != null && currentPhlx.compareTo(phlxMa50) < 0) {
                signals.add("⚠️ [섹터] 반도체 지수 단기 약세: 기술주 투심 둔화");
                score = Math.min(100.0, score + 10.0);
            }
        }
        return score;
    }

    private double analyzeLiquidity(IndexResponse dxy, IndexResponse usdKrw, List<String> signals) {
        double score = 50.0;

        if (isValid(dxy)) {
            BigDecimal currentDxy = getLastValue(dxy);
            BigDecimal dxyMa20 = calculateSMA(dxy, 20);
            if (dxyMa20 != null) {
                if (currentDxy.compareTo(dxyMa20.multiply(new BigDecimal("1.02"))) > 0) {
                    signals.add("⚠️ [유동성] 달러 인덱스 급등: 글로벌 유동성 축소 경계");
                    score += 25.0;
                } else if (currentDxy.compareTo(dxyMa20) < 0) {
                    signals.add("🟢 [유동성] 달러 인덱스 안정화: 유동성 환경 우호적");
                    score -= 15.0;
                }
            }
        }

        if (isValid(usdKrw)) {
            double krw = getLastValue(usdKrw).doubleValue();
            if (krw >= 1500.0) {
                signals.add("🚨 [환율] 환율 1,500원 돌파: 국내 증시 외국인 이탈 압력 심화");
                score += 25.0;
            } else if (krw >= 1450.0) {
                signals.add("🟠 [환율] 환율 1,450원 상회: 원화 약세에 따른 수급 부담");
                score += 10.0;
            }
        }
        return Math.max(0.0, Math.min(100.0, score));
    }

    private double analyzeMacro(IndexResponse us10y, IndexResponse us2y, IndexResponse wti, List<String> signals) {
        double score = 50.0;

        // 금리 속도 분석
        BigDecimal current10Y = getLastValue(us10y);
        BigDecimal us10yMa20 = calculateSMA(us10y, 20);

        if (us10yMa20 != null) {
            if (current10Y.compareTo(us10yMa20.multiply(new BigDecimal("1.05"))) > 0) {
                signals.add("⚠️ [금리] 10년물 국채 단기 급등: 기술주 밸류에이션 쇼크 우려");
                score += 25.0;
            } else if (current10Y.compareTo(us10yMa20.multiply(new BigDecimal("0.95"))) < 0) {
                signals.add("⚠️ [금리] 10년물 국채 단기 급락: 시장 내 강력한 경기 침체 공포 반영");
                score += 20.0;
            } else {
                signals.add("🟢 [금리] 국채 금리 박스권 안정: 주식 시장 할인율 부담 완화");
                score -= 10.0;
            }
        }

        // 장단기 금리차 (단순 역전뿐 아니라 심화 정도를 볼 수 있으나 우선 직관적 역전 판단)
        if (isValid(us2y)) {
            if (current10Y.compareTo(getLastValue(us2y)) < 0) {
                signals.add("🚨 [매크로] 장단기 금리차 역전: 역사적인 경기 침체 선행 신호 발동");
                score += 20.0;
            }
        }

        // 유가 분석 (상승 인플레 vs 하락 수요둔화)
        if (isValid(wti)) {
            BigDecimal currentWti = getLastValue(wti);
            BigDecimal wtiMa20 = calculateSMA(wti, 20);
            if (wtiMa20 != null) {
                if (currentWti.compareTo(wtiMa20.multiply(new BigDecimal("1.08"))) > 0) {
                    signals.add("🟠 [물가] WTI 유가 단기 급등: 인플레이션 재점화 우려");
                    score += 15.0;
                } else if (currentWti.compareTo(wtiMa20.multiply(new BigDecimal("0.90"))) < 0) {
                    signals.add("🟠 [매크로] WTI 유가 급락: 글로벌 수요 둔화(경기 침체) 징후");
                    score += 15.0;
                }
            }
        }
        return Math.max(0.0, Math.min(100.0, score));
    }

    // ==========================================
    // 유틸리티 메서드
    // ==========================================

    private String determineRegime(int score) {
        if (score <= 35) return "Risk-On (강세장 🚀)";
        if (score <= 60) return "Neutral (중립/조정 ⚖️)";
        if (score <= 80) return "Risk-Off (방어/현금확보 🛡️)";
        return "Panic (투매/위기 🚨)";
    }

    private boolean isValid(IndexResponse response) {
        return response != null && response.data() != null && !response.data().isEmpty();
    }

    private BigDecimal getLastValue(IndexResponse response) {
        return response.data().get(response.data().size() - 1).getValue();
    }

    // ✨ SMA 분모 버그 완벽 수정 (validCount 도입)
    private BigDecimal calculateSMA(IndexResponse response, int days) {
        if (!isValid(response) || response.data().size() < days) return null;

        List<IndexChartData> data = response.data();
        BigDecimal sum = BigDecimal.ZERO;
        int validCount = 0;
        int startIndex = data.size() - days;

        for (int i = startIndex; i < data.size(); i++) {
            BigDecimal value = data.get(i).getValue();
            if (value != null) {
                sum = sum.add(value);
                validCount++;
            }
        }

        if (validCount == 0) return null; // 분모가 0이 되는 것을 방지
        return sum.divide(BigDecimal.valueOf(validCount), 4, RoundingMode.HALF_UP);
    }
}