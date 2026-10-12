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

        // 2. 일봉 기반 단기 모멘텀 분석 (이격도, 최대 10점)
        if (isValidChart(dailyCharts, 20)) {
            score += scoreShortTermMomentum(dailyCharts, signals);
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

    // ── [2] 단기 모멘텀 (20일선 이격도) ────────────────────────
    private int scoreShortTermMomentum(List<StockChartDto> charts, List<String> signals) {
        double ma20 = calculateSMA(charts, 20);
        double curPrice = getLatestClose(charts);

        if (ma20 == 0) return 0;
        double disparity = (curPrice / ma20) * 100.0; // 이격도 계산

        if (disparity > 115) {
            signals.add(String.format("20일선 대비 과도한 급등 (이격도 %.1f%%) — 단기 과열 경계", disparity));
            return 4; // 단기적 쏠림이 너무 크면 오히려 조정 리스크가 커지므로 점수 제한
        } else if (disparity >= 100) {
            signals.add(String.format("20일선 상회 유지 (이격도 %.1f%%) — 단기 모멘텀 양호", disparity));
            return 10;
        } else if (disparity >= 95) {
            signals.add(String.format("20일선 근접 (이격도 %.1f%%) — 지지력 테스트 중", disparity));
            return 6;
        } else {
            signals.add(String.format("20일선 하회 (이격도 %.1f%%) — 단기 모멘텀 둔화", disparity));
            return 2;
        }
    }

    // ── [3] 단기 RSI 분석 (14일 기준) ─────────────────────────────────────
    private int scoreRsiMomentum(List<StockChartDto> charts, List<String> signals) {
        double rsi = calculateRSI(charts, 14);

        if (rsi > 70) {
            signals.add(String.format("단기 RSI %.1f — 과매수 구간 (단기 차익실현 주의)", rsi));
            return 1;
        } else if (rsi < 30) {
            signals.add(String.format("단기 RSI %.1f — 과매도 구간 (기술적 반등/매수 찬스)", rsi));
            return 5; // 역발상 매수로 높은 점수 부여
        } else if (rsi >= 50) {
            signals.add(String.format("단기 RSI %.1f — 매수 우위의 견조한 흐름", rsi));
            return 4;
        } else {
            signals.add(String.format("단기 RSI %.1f — 매도 우위의 약세 흐름", rsi));
            return 2;
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

    // ── [유틸] RSI 계산 (14일 기준) ──────────────────────────────────────────
    private double calculateRSI(List<StockChartDto> charts, int period) {
        double sumGain = 0;
        double sumLoss = 0;

        for (int i = charts.size() - period; i < charts.size(); i++) {
            double prevClose = charts.get(i - 1).close().doubleValue();
            double curClose = charts.get(i).close().doubleValue();
            double change = curClose - prevClose;

            if (change > 0) sumGain += change;
            else sumLoss += Math.abs(change);
        }

        double avgGain = sumGain / period;
        double avgLoss = sumLoss / period;

        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private boolean isInvalid(BigDecimal v) {
        return v == null || v.compareTo(BigDecimal.ZERO) <= 0;
    }
}