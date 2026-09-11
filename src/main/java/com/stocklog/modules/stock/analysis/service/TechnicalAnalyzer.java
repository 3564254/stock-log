package com.stocklog.modules.stock.analysis.service;

import com.stocklog.modules.stock.analysis.domain.technical.TechnicalScore;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TechnicalAnalyzer {

    // 💡 dailyCharts, weeklyCharts는 과거 -> 최신(Asc) 정렬 상태라고 가정
    TechnicalScore analyze(List<StockChartDto> dailyCharts, List<StockChartDto> weeklyCharts) {
        if (dailyCharts == null || dailyCharts.size() < 20) {
            return TechnicalScore.of(0, List.of("일봉 차트 데이터 부족"));
        }

        List<Double> dailyPrices = extractPrices(dailyCharts);
        List<Double> weeklyPrices = weeklyCharts != null ? extractPrices(weeklyCharts) : new ArrayList<>();

        List<String> signals = new ArrayList<>();
        int score = 0;

        score += scoreTrend(dailyPrices, weeklyPrices, signals); // 0~12점
        score += scoreCross(dailyPrices, signals);               // 0~10점
        score += scoreVolatility(dailyPrices, signals);          // 0~8점

        return TechnicalScore.of(score, signals);
    }

    // ── [1] 추세 분석: 일봉 MA 배열 및 주봉(중장기) 지지 상태 ──────────────────
    private int scoreTrend(List<Double> daily, List<Double> weekly, List<String> signals) {
        double cur = daily.get(daily.size() - 1);
        double ma5 = sma(daily, 5, 0);
        double ma20 = sma(daily, 20, 0);
        boolean hasDailyLong = daily.size() >= 60;
        double ma60 = hasDailyLong ? sma(daily, 60, 0) : 0;

        boolean hasWeeklyLong = weekly.size() >= 20;
        double weeklyMa20 = hasWeeklyLong ? sma(weekly, 20, 0) : 0; // 주봉 20선 (약 100일선 역할)

        // 완전 역배열 (하락 추세)
        if (ma5 < ma20 && cur < ma20) {
            if (hasDailyLong && cur < ma60) {
                signals.add("MA 완전 역배열 (주가 < MA5 < MA20 < MA60) — 강한 하락 추세");
                return 0;
            }
            signals.add("MA 역배열 (MA5 < MA20) — 하락 추세");
            return 1;
        }

        // 완전 정배열 (상승 추세)
        if (ma5 > ma20 && cur > ma20) {
            if (hasDailyLong && cur > ma60 && ma20 > ma60) {
                int score = 10;
                signals.add(String.format("MA 완전 정배열 (현재 %.0f / MA60 %.0f) — 강한 상승 추세", cur, ma60));

                // 주봉 20선 위에 있다면 장기 추세도 완벽하다고 판단하여 만점 부여
                if (hasWeeklyLong && cur > weeklyMa20) {
                    signals.add(String.format("주봉 20선 지지 (MA20 %.0f) — 중장기 추세 안정적", weeklyMa20));
                    score = 12;
                }
                return score;
            }
            signals.add(String.format("단기 상승 추세 (MA5 > MA20, 현재 %.0f)", cur));
            return 8;
        }

        // 혼조: 주가만 MA20 위 (초기 회복)
        if (cur > ma20 && ma5 <= ma20) {
            signals.add("MA20 돌파 — 회복 초기 국면 (MA5 아직 미돌파)");
            return 5;
        }

        // 혼조: MA 정배열 중 주가가 MA20 아래 (단기 조정)
        if (ma5 > ma20 && cur <= ma20) {
            signals.add("정배열 중 MA20 하회 — 눌림목 지지 여부 확인 필요");
            return 4;
        }

        return 3;
    }

    // ── [2] 크로스 판정: 일봉 기준 정확한 타점 분석 ───────────────────────────
    private int scoreCross(List<Double> p, List<String> signals) {
        if (p.size() < 22) return 0; // MA20 계산 + 이전 봉 비교를 위한 최소치

        // offset 0 = 오늘(가장 최신), offset 1 = 어제
        double ma5Now = sma(p, 5, 0);
        double ma20Now = sma(p, 20, 0);

        double ma5Prev = sma(p, 5, 1);
        double ma20Prev = sma(p, 20, 1);

        boolean crossedUpNow = ma5Now > ma20Now;
        boolean crossedUpPrev = ma5Prev > ma20Prev;

        // 오늘 골든크로스 발생
        if (crossedUpNow && !crossedUpPrev) {
            signals.add(String.format("🔥 일봉 골든크로스 포착 (MA5 %.0f > MA20 %.0f)", ma5Now, ma20Now));
            return 10;
        }
        // 오늘 데드크로스 발생
        if (!crossedUpNow && crossedUpPrev) {
            signals.add(String.format("⚠️ 일봉 데드크로스 포착 (MA5 %.0f < MA20 %.0f)", ma5Now, ma20Now));
            return 0;
        }

        // 골든크로스 유지 중
        if (crossedUpNow) {
            int daysSince = countDaysSinceCross(p, true);
            if (daysSince <= 10) { // 약 2주 이내
                signals.add(String.format("골든크로스 초기 유지 중 (%d일 경과)", daysSince));
                return 6;
            }
            signals.add("골든크로스 장기 유지 — 추세 안착");
            return 4;
        }

        // 데드크로스 유지 중
        signals.add("데드크로스 유지 중 — 매수 신중");
        return 1;
    }

    // ── [3] 변동성(볼린저 스퀴즈) 평가 ────────────────────────────────────────
    private int scoreVolatility(List<Double> p, List<String> signals) {
        int period = Math.min(20, p.size()); // 최근 20일 기준
        if (period == 0) return 0;

        int start = p.size() - period;
        double sum = 0;
        for (int i = start; i < p.size(); i++) {
            sum += p.get(i);
        }
        double mean = sum / period;

        double varSum = 0;
        for (int i = start; i < p.size(); i++) {
            varSum += Math.pow(p.get(i) - mean, 2);
        }
        double stdDev = Math.sqrt(varSum / period);
        double cv = (mean > 0) ? (stdDev / mean) * 100 : 0;

        if (cv < 5) {
            signals.add(String.format("볼린저 스퀴즈 (CV %.1f%%) — 극단적 저변동, 방향성 폭발 대기", cv));
            return 4;
        }
        if (cv < 12) { signals.add(String.format("안정적 변동성 (CV %.1f%%)", cv)); return 8; }
        if (cv < 20) { signals.add(String.format("보통 변동성 (CV %.1f%%)", cv)); return 5; }
        if (cv < 35) { signals.add(String.format("높은 변동성 (CV %.1f%%)", cv)); return 2; }

        signals.add(String.format("초고변동성 (CV %.1f%%) — 리스크 관리 필수", cv));
        return 0;
    }

    // ── [유틸] 과거 시점(offset) 기준 이동평균선 산출 ──────────────────────────
    private double sma(List<Double> p, int period, int offset) {
        if (p.size() < period + offset) return 0.0;

        int end = p.size() - 1 - offset;
        int start = end - period + 1;

        double sum = 0;
        for (int i = start; i <= end; i++) {
            sum += p.get(i);
        }
        return sum / period;
    }

    // ── [유틸] 가장 최근 크로스 경과 일수 탐색 (개선됨) ────────────────────────────────
    private int countDaysSinceCross(List<Double> p, boolean goldenCross) {
        // MA20을 계산할 수 있는 최대 과거 시점(offset) 계산
        int maxOffset = p.size() - 20;
        int maxLookback = Math.min(60, maxOffset);

        // 탐색할 수 있는 데이터가 부족하면 탐색 중단
        if (maxLookback <= 1) return 99;

        for (int i = 1; i < maxLookback; i++) {
            double ma5Cur = sma(p, 5, i);
            double ma20Cur = sma(p, 20, i);
            double ma5Prev = sma(p, 5, i + 1);
            double ma20Prev = sma(p, 20, i + 1);

            boolean curState = ma5Cur > ma20Cur;
            boolean prevState = ma5Prev > ma20Prev;

            if (goldenCross && curState && !prevState) return i;
            if (!goldenCross && !curState && prevState) return i;
        }
        return 99;
    }

    // ── [유틸] DTO에서 종가 추출 ─────────────────────────────────────────────
    private List<Double> extractPrices(List<StockChartDto> charts) {
        return charts.stream()
                .map(c -> c.close().doubleValue())
                .collect(Collectors.toList());
    }
}