package com.stocklog.domain.stock.analysis.domain.technical;

import com.stocklog.domain.stock.dto.vo.price.StockChartData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TechnicalAnalyzer {

    public TechnicalScore analyze(List<StockChartData> weeklyBars) {
        if (weeklyBars == null || weeklyBars.size() < 20) {
            return TechnicalScore.of(0, List.of("차트 데이터 부족"));
        }

        List<Double> prices = weeklyBars.stream()
                .map(b -> b.getValue().doubleValue())
                .collect(Collectors.toList());
        Collections.reverse(prices); // get(0) = 가장 최근 주봉

        List<String> signals = new ArrayList<>();
        int score = 0;

        score += scoreTrend(prices, signals);       // 0~12점
        score += scoreCross(prices, signals);        // 0~10점
        score += scoreVolatility(prices, signals);  // 0~8점

        return TechnicalScore.of(score, signals);
    }

    // ── [1] 추세 분석: MA 배열 상태 ──────────────────────────────────────────
    // 핵심 수정: 조건 순서 재정립 — 역배열 체크가 정배열 체크보다 먼저 오도록
    private int scoreTrend(List<Double> p, List<String> signals) {
        double cur  = p.get(0);
        double ma5  = sma(p, 5);
        double ma20 = sma(p, 20);
        boolean hasLong = p.size() >= 60;
        double  ma60 = hasLong ? sma(p, 60) : 0;

        // 완전 역배열: MA5 < MA20, 주가도 MA20 아래
        if (ma5 < ma20 && cur < ma20) {
            if (hasLong && cur < ma60) {
                signals.add("MA 완전 역배열 (주가 < MA5 < MA20 < MA60) — 강한 하락 추세");
                return 0;
            }
            signals.add("MA 역배열 (MA5 < MA20) — 하락 추세");
            return 1;
        }

        // 완전 정배열: MA5 > MA20, 주가도 MA20 위
        if (ma5 > ma20 && cur > ma20) {
            if (hasLong && cur > ma60 && ma20 > ma60) {
                signals.add(String.format("MA 완전 정배열 (MA5 > MA20 > MA60) — 강한 상승 추세 (현재 %.0f / MA60 %.0f)", cur, ma60));
                return 12;
            }
            signals.add(String.format("단기 상승 추세 (MA5 > MA20, 현재 %.0f / MA20 %.0f)", cur, ma20));
            return 8;
        }

        // 혼조: 주가만 MA20 위 (MA5는 아직 MA20 아래 — 초기 회복 국면)
        if (cur > ma20 && ma5 <= ma20) {
            signals.add(String.format("MA20 상회 — 회복 초기 국면 (MA5 아직 미돌파, MA20 %.0f)", ma20));
            return 5;
        }

        // 혼조: MA5 > MA20이지만 주가가 MA20 아래 (단기 조정)
        if (ma5 > ma20 && cur <= ma20) {
            signals.add(String.format("MA 정배열 중 주가 일시 하회 — 눌림 여부 확인 필요 (MA20 %.0f)", ma20));
            return 4;
        }


        return 3;
    }

    // ── [2] 크로스 판정: 직전 봉 비교 방식 ──────────────────────────────────
    // 핵심 수정: subList(5, ...) 방식 → 직전 봉(index 1) 기준 정확한 크로스 탐지
    private int scoreCross(List<Double> p, List<String> signals) {
        if (p.size() < 22) return 0; // MA20 + 직전봉 최소 요구

        double ma5Now   = sma(p, 5);
        double ma20Now  = sma(p, 20);

        // 직전 봉 기준 MA (index 1부터 시작하는 서브리스트)
        List<Double> prev = p.subList(1, p.size());
        double ma5Prev  = sma(prev, 5);
        double ma20Prev = sma(prev, 20);

        boolean crossedUpNow  = ma5Now  > ma20Now;
        boolean crossedUpPrev = ma5Prev > ma20Prev;

        // 이번 봉에서 골든크로스 발생
        if (crossedUpNow && !crossedUpPrev) {
            signals.add(String.format("골든크로스 발생 (MA5 %.0f > MA20 %.0f)", ma5Now, ma20Now));
            return 10;
        }
        // 이번 봉에서 데드크로스 발생
        if (!crossedUpNow && crossedUpPrev) {
            signals.add(String.format("데드크로스 발생 (MA5 %.0f < MA20 %.0f)", ma5Now, ma20Now));
            return 0;
        }
        // 골든크로스 유지 중: 크로스 이후 경과 주 수로 신뢰도 차별화
        if (crossedUpNow) {
            int weeksSince = countWeeksSinceCross(p, true);
            if (weeksSince <= 4) {
                signals.add(String.format("골든크로스 유지 중 (%d주 경과)", weeksSince));
                return 6;
            }
            signals.add("골든크로스 장기 유지 — 추세 안정적");
            return 4;
        }
        // 데드크로스 유지 중
        signals.add("데드크로스 유지 중 — 매수 신중");
        return 1;
    }

    /**
     * 가장 최근 크로스가 몇 봉 전에 발생했는지 탐색.
     * goldenCross=true  → 골든크로스 발생 시점 탐색
     * goldenCross=false → 데드크로스  발생 시점 탐색
     */
    private int countWeeksSinceCross(List<Double> p, boolean goldenCross) {
        for (int i = 1; i < p.size() - 20; i++) {
            List<Double> cur  = p.subList(i, p.size());
            List<Double> prev = p.subList(i + 1, p.size());
            if (prev.size() < 20) break;
            boolean curState  = sma(cur, 5)  > sma(cur, 20);
            boolean prevState = sma(prev, 5) > sma(prev, 20);
            if (goldenCross && curState && !prevState)  return i;
            if (!goldenCross && !curState && prevState) return i;
        }
        return 99; // 탐색 범위 내 크로스 없음
    }

    // ── [3] 변동성 평가 ──────────────────────────────────────────────────────
    private int scoreVolatility(List<Double> p, List<String> signals) {
        List<Double> recent = p.subList(0, Math.min(26, p.size()));
        double mean   = recent.stream().mapToDouble(Double::doubleValue).average().orElse(1);
        double stdDev = Math.sqrt(recent.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0));
        double cv = mean > 0 ? stdDev / mean * 100 : 0;

        if (cv < 5) {
            // 극단적 저변동 = 볼린저 스퀴즈 유사 — 방향 불확실, 중간 점수
            signals.add(String.format("변동성 극히 낮음 CV=%.1f%% — 방향성 폭발 대기 가능성", cv));
            return 4;
        }
        if (cv < 12)  { signals.add(String.format("변동성 낮음 CV=%.1f%%",       cv)); return 8; }
        if (cv < 20) { signals.add(String.format("변동성 보통 CV=%.1f%%",       cv)); return 5; }
        if (cv < 35) { signals.add(String.format("변동성 높음 CV=%.1f%%",       cv)); return 2; }
        signals.add(String.format("변동성 매우 높음 CV=%.1f%% — 리스크 관리 필수", cv));
        return 0;
    }

    private double sma(List<Double> p, int n) {
        return p.subList(0, Math.min(n, p.size()))
                .stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}