package com.stocklog.modules.stock.analysis.analyzer;

import com.stocklog.modules.stock.analysis.score.TechnicalScore;
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
    public TechnicalScore analyze(List<StockChartDto> dailyCharts, List<StockChartDto> weeklyCharts) {
        if (dailyCharts == null || dailyCharts.size() < 20) {
            return TechnicalScore.of(0, List.of("일봉 차트 데이터 부족"));
        }

        List<Double> dailyPrices = extractPrices(dailyCharts);
        List<Double> weeklyPrices = weeklyCharts != null ? extractPrices(weeklyCharts) : new ArrayList<>();

        List<String> signals = new ArrayList<>();
        int score = 0;

        score += scoreTrend(dailyPrices, weeklyPrices, signals); // 0~12점
        score += scoreCross(dailyPrices, signals);               // 0~10점 (MA20 × MA60)
        score += scoreVolatility(dailyPrices, signals);          // 0~8점 (평소 대비 밴드 폭)

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

    // ── [2] 크로스 판정: 중기 이평(MA20 × MA60) ───────────────────────────────
    // 예전에는 MA5 × MA20을 봤는데, [1] 추세의 MA5/MA20 배열, 모멘텀의 20일선 이격도와 사실상 같은 신호라
    // 20일선 하나에 점수가 몰렸다(하루 이틀 20일선 아래로 빠지면 최대 30점 가까이 동시에 빠짐).
    // 크로스는 한 단계 긴 MA20 × MA60으로 보아 '중기 추세 전환'을 담당하게 한다.
    private static final int CROSS_FAST = 20;
    private static final int CROSS_SLOW = 60;

    private int scoreCross(List<Double> p, List<String> signals) {
        if (p.size() < CROSS_SLOW + 2) {
            signals.add("중기 크로스 판단 데이터 부족 (일봉 62개 미만)");
            return 5; // 판단 불가는 중립값
        }
        double fastNow = sma(p, CROSS_FAST, 0);
        double slowNow = sma(p, CROSS_SLOW, 0);
        double fastPrev = sma(p, CROSS_FAST, 1);
        double slowPrev = sma(p, CROSS_SLOW, 1);

        boolean upNow = fastNow > slowNow;
        boolean upPrev = fastPrev > slowPrev;

        if (upNow && !upPrev) {
            signals.add(String.format("🔥 일봉 골든크로스 포착 (MA20 %.0f > MA60 %.0f) — 중기 추세 전환", fastNow, slowNow));
            return 10;
        }
        if (!upNow && upPrev) {
            signals.add(String.format("⚠️ 일봉 데드크로스 포착 (MA20 %.0f < MA60 %.0f) — 중기 추세 약화", fastNow, slowNow));
            return 0;
        }
        if (upNow) {
            int daysSince = countDaysSinceCross(p, true);
            if (daysSince <= 15) { // 약 3주 이내
                signals.add(String.format("골든크로스 초기 유지 중 (MA20 > MA60, %d일 경과)", daysSince));
                return 8;
            }
            signals.add("골든크로스 장기 유지 — 중기 추세 안착 (MA20 > MA60)");
            return 7;
        }
        signals.add("데드크로스 유지 중 (MA20 < MA60) — 중기 추세 약세");
        return 2;
    }

    // ── [3] 변동성: '그 종목의 평소' 대비 볼린저 밴드 폭 ───────────────────────
    // 예전에는 20일 종가의 변동계수(CV) 절대값으로 매겨서, 평소 CV가 5% 미만인 대형 우량주는
    // 늘 '볼린저 스퀴즈' 4점, 출렁이는 중소형주가 만점(8점)을 받는 역전이 있었다.
    // 이제는 최근 밴드 폭이 그 종목의 지난 최대 120일 분포에서 어디쯤인지(백분위)로 본다.
    private static final int BAND_PERIOD = 20;
    private static final int BAND_LOOKBACK = 120;
    private static final int BAND_MIN_SAMPLES = 40;
    private static final double EXTREME_DAILY_VOL = 5.0; // 일간 수익률 표준편차(%)가 이 이상이면 종목 자체가 초고변동

    private int scoreVolatility(List<Double> p, List<String> signals) {
        int samples = Math.min(BAND_LOOKBACK, p.size() - BAND_PERIOD + 1);
        if (samples < BAND_MIN_SAMPLES) {
            signals.add("변동성 비교 데이터 부족 — 평소 대비 판단 보류");
            return 5;
        }
        double[] widths = new double[samples];
        for (int i = 0; i < samples; i++) widths[i] = bandWidth(p, samples - 1 - i); // 오래된 → 최신
        double now = widths[samples - 1];
        int below = 0;
        for (double w : widths) if (w < now) below++;
        double pct = 100.0 * below / samples;                       // 0 = 최근 중 가장 좁음, 100 = 가장 넓음

        double dailyVol = dailyReturnStdPct(p, BAND_PERIOD);
        if (dailyVol >= EXTREME_DAILY_VOL) {
            signals.add(String.format("초고변동성 (일간 변동폭 %.1f%%) — 리스크 관리 필수", dailyVol));
            return 0;
        }
        if (pct <= 20) {
            signals.add(String.format("볼린저 스퀴즈 (밴드 폭 최근 %d일 중 하위 %.0f%%) — 방향성 대기", samples, pct));
            return 6;
        }
        if (pct <= 60) {
            signals.add(String.format("평소 수준 변동성 (밴드 폭 백분위 %.0f%%)", pct));
            return 8;
        }
        if (pct <= 85) {
            signals.add(String.format("변동성 확대 (밴드 폭 백분위 %.0f%%) — 평소보다 출렁임이 큼", pct));
            return 5;
        }
        signals.add(String.format("변동성 급확대 (밴드 폭 백분위 %.0f%%) — 급등락 구간", pct));
        return 2;
    }

    /** offset일 전 기준 볼린저 밴드 폭 = 4σ / MA (%). */
    private double bandWidth(List<Double> p, int offset) {
        double mean = sma(p, BAND_PERIOD, offset);
        if (mean <= 0) return 0;
        int end = p.size() - 1 - offset;
        double var = 0;
        for (int i = end - BAND_PERIOD + 1; i <= end; i++) var += Math.pow(p.get(i) - mean, 2);
        return 4 * Math.sqrt(var / BAND_PERIOD) / mean * 100.0;
    }

    /** 최근 n일 일간 수익률의 표준편차 (%) */
    private double dailyReturnStdPct(List<Double> p, int n) {
        int start = Math.max(1, p.size() - n);
        List<Double> r = new ArrayList<>();
        for (int i = start; i < p.size(); i++) {
            double prev = p.get(i - 1);
            if (prev > 0) r.add((p.get(i) / prev - 1) * 100.0);
        }
        if (r.size() < 2) return 0;
        double m = r.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double v = r.stream().mapToDouble(x -> (x - m) * (x - m)).sum() / (r.size() - 1);
        return Math.sqrt(v);
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

    // ── [유틸] 가장 최근 크로스(MA20 × MA60) 경과 일수 ─────────────────────────
    private int countDaysSinceCross(List<Double> p, boolean goldenCross) {
        int maxOffset = p.size() - CROSS_SLOW - 1;
        int maxLookback = Math.min(60, maxOffset);
        if (maxLookback <= 1) return 99;

        for (int i = 1; i < maxLookback; i++) {
            boolean curState = sma(p, CROSS_FAST, i) > sma(p, CROSS_SLOW, i);
            boolean prevState = sma(p, CROSS_FAST, i + 1) > sma(p, CROSS_SLOW, i + 1);
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