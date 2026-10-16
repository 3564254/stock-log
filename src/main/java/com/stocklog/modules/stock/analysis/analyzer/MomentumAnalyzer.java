package com.stocklog.modules.stock.analysis.analyzer;

import com.stocklog.modules.stock.analysis.score.MomentumScore;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class MomentumAnalyzer {

    // 💡 dailyCharts는 과거 -> 최신 (Asc) 정렬 상태로 주입
    public MomentumScore analyze(List<StockChartDto> dailyCharts, StockPriceDto priceDto) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        // 1. 52주 포지션 평가 (최대 10점)
        double w52Position = calculateW52Position(priceDto);
        score += scoreW52Position(priceDto, w52Position, signals);

        // 2. 중기 가격 모멘텀 (3개월 수익률, 최대 10점)
        //    예전에는 20일선 이격도를 봤는데, 기술적 분석의 MA5/MA20 배열과 같은 신호라 20일선에 점수가 몰렸다.
        if (isValidChart(dailyCharts, RETURN_DAYS + 1)) {
            score += scoreMediumTermReturn(dailyCharts, signals);
        }

        // 3. 단기 RSI 상대강도지수 분석 (최대 5점)
        if (isValidChart(dailyCharts, 15)) {
            score += scoreRsiMomentum(dailyCharts, signals);
        }

        // 4. 단기 거래량 급증 패턴 분석 (최대 5점)
        if (isValidChart(dailyCharts, 20)) {
            score += scoreVolumeBreakout(dailyCharts, w52Position, signals);
        }

        return MomentumScore.of(score, signals);
    }

    // ── [1] 52주 포지션 계산 및 평가 ─────────────────────────────
    private double calculateW52Position(StockPriceDto p) {
        if (isInvalid(p.w52High()) || isInvalid(p.w52Low())) return -1;
        double high = p.w52High().doubleValue();
        double low = p.w52Low().doubleValue();
        double cur = p.currentPrice().doubleValue();

        if (high - low == 0) return 0.5;
        return (cur - low) / (high - low);
    }

    private int scoreW52Position(StockPriceDto p, double pos, List<String> signals) {
        if (pos < 0) return 0;
        double cur = p.currentPrice().doubleValue();
        double high = p.w52High().doubleValue();

        if (cur > high) {
            signals.add(String.format("52주 신고가 갱신 (현재가 %.0f) — 강한 상승 모멘텀 유지", cur));
            return 10;
        }
        if (pos >= 0.80) { signals.add("52주 고가권 — 상승 추세 유효"); return 8; }
        if (pos >= 0.50) { signals.add("52주 중간권 — 방향성 탐색 중"); return 5; }
        if (pos >= 0.20) { signals.add("52주 하단권 — 기술적 반등 대기"); return 2; }

        signals.add("52주 신저가 부근 — 바닥 확인 전까지 관망 권장");
        return 0;
    }

    // ── [2] 중기 가격 모멘텀 (약 3개월 = 60거래일 수익률) ─────────────────────
    private static final int RETURN_DAYS = 60;

    private int scoreMediumTermReturn(List<StockChartDto> charts, List<String> signals) {
        double cur = getLatestClose(charts);
        double past = charts.get(charts.size() - 1 - RETURN_DAYS).close().doubleValue();
        if (past <= 0) return 0;
        double ret = (cur / past - 1) * 100.0;

        // 20일선에서 너무 멀어진 급등은 점수와 별개로 경고만 남긴다 (점수는 3개월 흐름으로)
        if (isValidChart(charts, 20)) {
            double ma20 = calculateSMA(charts, 20);
            double disparity = ma20 > 0 ? cur / ma20 * 100.0 : 100.0;
            if (disparity > 115) {
                signals.add(String.format("20일선 대비 과도한 급등 (이격도 %.1f%%) — 단기 과열 경계", disparity));
            }
        }

        if (ret >= 15) { signals.add(String.format("3개월 수익률 %+.1f%% — 강한 중기 상승 탄력", ret)); return 10; }
        if (ret >= 5)  { signals.add(String.format("3개월 수익률 %+.1f%% — 중기 상승 흐름", ret)); return 8; }
        if (ret >= -5) { signals.add(String.format("3개월 수익률 %+.1f%% — 중기 횡보", ret)); return 5; }
        if (ret >= -15){ signals.add(String.format("3개월 수익률 %+.1f%% — 중기 하락 흐름", ret)); return 2; }
        signals.add(String.format("3개월 수익률 %+.1f%% — 강한 중기 하락", ret));
        return 0;
    }

    // ── [3] RSI (14일, Wilder 방식) ───────────────────────────────────────
    // 모멘텀 점수이므로 '강할수록 높게'로 일관되게 매긴다. 과열(70+)은 강하지만 부담이 있어 한 단계 낮추고,
    // 과매도(30-)는 반등 가능성이 있어도 모멘텀 자체는 약하므로 낮게 준다 (예전엔 과매도가 최고점이었다).
    private int scoreRsiMomentum(List<StockChartDto> charts, List<String> signals) {
        double rsi = calculateRSI(charts, 14);
        if (rsi > 70) {
            signals.add(String.format("단기 RSI %.1f — 과매수 구간 (강하지만 단기 과열)", rsi));
            return 4;
        } else if (rsi >= 50) {
            signals.add(String.format("단기 RSI %.1f — 매수 우위의 견조한 흐름", rsi));
            return 5;
        } else if (rsi >= 30) {
            signals.add(String.format("단기 RSI %.1f — 매도 우위의 약세 흐름", rsi));
            return 2;
        } else {
            signals.add(String.format("단기 RSI %.1f — 과매도 구간 (반등 여지는 있지만 모멘텀은 약함)", rsi));
            return 1;
        }
    }

    // ── [4] 단기 거래량 급증 판별 ───────────────────────────────────────
    private int scoreVolumeBreakout(List<StockChartDto> dailyCharts, double w52Position, List<String> signals) {
        int recentDays = 5;
        double avgVolume = dailyCharts.stream()
                .limit(dailyCharts.size() - recentDays)
                .mapToDouble(c -> c.volume().doubleValue())
                .average().orElse(0.0);

        double latestVolume = dailyCharts.get(dailyCharts.size() - 1).volume().doubleValue();

        if (avgVolume > 0 && latestVolume > avgVolume * 3) {
            if (w52Position <= 0.4) {
                signals.add("🚀 바닥권 거래량 300% 이상 폭발 — 강력한 매집 시그널");
                return 5;
            } else if (w52Position >= 0.8) {
                signals.add("⚠️ 고점권 거래량 폭발 — 세력의 차익실현(설거지) 가능성 경계");
                return 0;
            } else {
                signals.add("유의미한 거래량 유입 — 모멘텀 변화 발생");
                return 3;
            }
        }

        signals.add("평이한 거래량 흐름");
        return 2;
    }

    // ── [유틸] 차트 데이터 유효성 검증 ────────────────────────────────────
    private boolean isValidChart(List<StockChartDto> charts, int requiredSize) {
        return charts != null && charts.size() >= requiredSize;
    }

    // ── [유틸] 최신 종가 반환 ──────────────────────────────────────────
    private double getLatestClose(List<StockChartDto> charts) {
        return charts.get(charts.size() - 1).close().doubleValue();
    }

    // ── [유틸] 이동평균선(SMA) 계산 ──────────────────────────────────────────
    private double calculateSMA(List<StockChartDto> charts, int period) {
        List<StockChartDto> subList = charts.subList(charts.size() - period, charts.size());
        return subList.stream()
                .mapToDouble(c -> c.close().doubleValue())
                .average()
                .orElse(0.0);
    }

    // ── [유틸] RSI (Wilder 평활) ─────────────────────────────────────────────
    // 첫 period개 변화량의 단순 평균으로 시작해, 이후 (이전 평균 × (n-1) + 이번 값) / n 으로 이어 간다.
    private double calculateRSI(List<StockChartDto> charts, int period) {
        int n = charts.size();
        if (n <= period) return 50.0;
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double ch = charts.get(i).close().doubleValue() - charts.get(i - 1).close().doubleValue();
            if (ch > 0) avgGain += ch; else avgLoss -= ch;
        }
        avgGain /= period;
        avgLoss /= period;
        for (int i = period + 1; i < n; i++) {
            double ch = charts.get(i).close().doubleValue() - charts.get(i - 1).close().doubleValue();
            avgGain = (avgGain * (period - 1) + Math.max(ch, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-ch, 0)) / period;
        }
        if (avgLoss == 0) return avgGain == 0 ? 50.0 : 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private boolean isInvalid(BigDecimal v) {
        return v == null || v.compareTo(BigDecimal.ZERO) <= 0;
    }
}