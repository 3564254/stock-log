package com.stocklog.domain.stock.analysis.domain.momentum;

import com.stocklog.domain.stock.dto.vo.analysis.StockSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class MomentumAnalyzer {

    public MomentumScore analyze(StockSnapshot s) {
        List<String> signals = new ArrayList<>();
        int score = 0;

        // 1. 주가의 52주 내 위치(0.0 ~ 1.0)를 먼저 계산하여 하위 분석에 공통으로 사용
        double w52Position = calculateW52Position(s);

        score += scoreW52Position(s, w52Position, signals);                 // 0~15점
        score += scoreMarketCap(s, signals, s.getMarket().isDomestic());  // 0~8점

        // ✨ 고점 거래량 함정(Trap) 판별을 위해 position 값을 함께 넘김
        score += scoreVolumeTurnover(s, signals, s.getMarket().isDomestic(), w52Position); // 0~7점

        return MomentumScore.of(score, signals);
    }

    // ── [0] 52주 포지션 계산 유틸 ─────────────────────────────────────────────
    private double calculateW52Position(StockSnapshot s) {
        if (isInvalid(s.getW52High()) || isInvalid(s.getW52Low())) return -1;
        double high = s.getW52High().doubleValue();
        double low = s.getW52Low().doubleValue();
        double cur = s.getCurrentPrice().doubleValue();

        if (high - low == 0) return 0.5;
        return (cur - low) / (high - low);
    }

    // ── [1] 52주 포지션 평가 (최대 15점) ──────────────────────────────────────
    private int scoreW52Position(StockSnapshot s, double pos, List<String> signals) {
        if (pos < 0) return 0;
        double cur = s.getCurrentPrice().doubleValue();
        double high = s.getW52High().doubleValue();

        if (cur > high) {
            signals.add(String.format("52주 신고가 갱신 (현재 %.0f / 전고 %.0f) — 강한 상승 모멘텀", cur, high));
            return 15;
        }
        if (pos >= 0.85) { signals.add(String.format("52주 고가권 (상위 %.0f%%)", (1 - pos) * 100)); return 12; }
        if (pos >= 0.65) { signals.add("52주 상단권 — 추세 양호"); return 9; }
        if (pos >= 0.40) { signals.add("52주 중간권"); return 6; }
        if (pos >= 0.20) { signals.add(String.format("52주 하단권 (고점 대비 -%.0f%%)", (high - cur) / high * 100)); return 3; }

        signals.add(String.format("52주 저점권 (고점 대비 -%.0f%%) — 바닥 확인 필요", (high - cur) / high * 100));
        return 0;
    }

    // ── [2] 시가총액 평가 (최대 8점 - 올라운더 최적화) ─────────────────────────
    private int scoreMarketCap(StockSnapshot s, List<String> signals, boolean domestic) {
        if (isInvalid(s.getMarketCap())) return 0;
        double cap = s.getMarketCap().doubleValue();

        if (domestic) {
            double capTril = cap / 1_000_000.0; // 조 원
            // ✨ 대형주/중형주에 골고루 고득점 부여 (성장성 + 안정성 밸런스)
            if (capTril >= 5.0) { signals.add(String.format("대형주 (시총 %.1f조원)", capTril)); return 7; }
            if (capTril >= 1.0) { signals.add(String.format("중대형주 (%.1f조원) — 성장/안정 밸런스", capTril)); return 8; } // 만점
            if (capTril >= 0.3) { signals.add(String.format("중소형주 (%.1f조원)", capTril)); return 5; }
            signals.add("소형주 — 유동성 주의"); return 2;
        } else {
            double capBil = cap / 1_000_000_000.0; // 억 달러
            if (capBil >= 1_000) { signals.add(String.format("메가캡 ($%.1fT)", capBil / 1_000)); return 7; }
            if (capBil >= 50) { signals.add(String.format("대형주 Large-Cap ($%.0fB)", capBil)); return 8; } // 만점
            if (capBil >= 10) { signals.add(String.format("중형주 Mid-Cap ($%.0fB) — 성장성 기대", capBil)); return 8; } // 만점
            if (capBil >= 2) { signals.add(String.format("소형주 Small-Cap ($%.1fB)", capBil)); return 4; }
            signals.add("초소형주 Micro-Cap — 유동성 위험"); return 1;
        }
    }

    // ── [3] 거래 회전율 평가 (최대 7점 - 고점 설거지 회피) ──────────────────────
    private int scoreVolumeTurnover(StockSnapshot s, List<String> signals, boolean domestic, double w52Position) {
        if (isInvalid(s.getVolume()) || isInvalid(s.getMarketCap()) || isInvalid(s.getCurrentPrice())) return 0;

        double tradingValue = s.getVolume().multiply(s.getCurrentPrice()).doubleValue();
        double marketCap = domestic ? s.getMarketCap().doubleValue() * 1_000_000.0 : s.getMarketCap().doubleValue();

        if (marketCap <= 0) return 0;
        double turnover = tradingValue / marketCap;

        // 시장별 고회전율 기준 (국내 3%, 해외 1.5%)
        double highTurnoverCriteria = domestic ? 0.03 : 0.015;
        double midTurnoverCriteria = domestic ? 0.01 : 0.005;

        // ✨ 고점 거래량 함정 방어 로직
        if (turnover > highTurnoverCriteria) {
            if (w52Position >= 0.8) {
                signals.add(String.format("⚠️ [경고] 고점 부근 고회전율 (%.1f%%) — 세력 분산(차익실현) 주의", turnover * 100));
                return 0; // 페널티 또는 0점
            } else if (w52Position <= 0.4) {
                signals.add(String.format("🚀 [강세] 바닥권 거래량 폭발 (%.1f%%) — 강력한 매집/추세 전환 시그널", turnover * 100));
                return 7; // 강력한 가점
            } else {
                signals.add(String.format("거래 매우 활발 (회전율 %.1f%%) — 단기 수급 집중", turnover * 100));
                return 6;
            }
        }

        if (turnover > midTurnoverCriteria) {
            signals.add(String.format("거래 활발 (회전율 %.1f%%) — 수급 양호", turnover * 100));
            return 5;
        }

        signals.add(String.format("거래 보통/저조 (회전율 %.2f%%)", turnover * 100));
        return 2;
    }

    private boolean isInvalid(BigDecimal v) {
        return v == null || v.compareTo(BigDecimal.ZERO) <= 0;
    }
}