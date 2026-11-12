package com.stocklog.modules.stock.screening.dto;

import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.momentum.MomentumRank;
import com.stocklog.modules.stock.momentum.MomentumService;
import com.stocklog.modules.stock.analysis.signal.trading.Verdict;
import com.stocklog.modules.stock.screening.entity.ScreeningResult;

import java.math.BigDecimal;

/** 홈 화면 진입 후보 카드의 한 줄 */
public record ScreeningCandidate(
        String code,
        String name,
        Nation nation,
        boolean indexEtf,
        int marketCapRank,
        BigDecimal currentPrice,
        Verdict verdict,
        String verdictLabel,
        String entryTypeTitle,
        ChartPhase chartPhase,
        String phaseTitle,
        int score,                  // 0~60 (모멘텀 + 기술)
        double primaryEntry,
        double stopLoss,
        double primaryTarget,
        double riskReward,
        double trailingStop,        // 보유 손절선 (없으면 0)
        double holdTarget,          // 보유 목표가 (없으면 0)
        double zoneLow,             // 진입 구간 하단 (없으면 0)
        double zoneHigh,            // 진입 구간 상단 (없으면 0)
        String headline,
        boolean newCandidate,       // 직전 분석 때는 진입 후보가 아니었던 종목 (NEW 배지)
        Integer momentumRank,       // 미국 12-1개월 모멘텀 순위
        Double momentumPct,         // 순위 / 전체 × 100 (작을수록 강함)
        boolean momentumTop,        // 상위 20% 이내
        String sector,              // 종목 마스터의 산업 섹터 (없으면 null)
        BigDecimal marketCap,       // 원화 기준 시가총액 (없으면 null)
        Double rsi2,                // 2일 RSI
        boolean rsi2Dip,            // 상승 추세 안 RSI2 < 10
        boolean rsi2Fear
) {
    /** RSI2 값을 붙인 복사본 */
    public ScreeningCandidate withRsi2(Double r, boolean dip, boolean fear) {
        return new ScreeningCandidate(code, name, nation, indexEtf, marketCapRank, currentPrice, verdict, verdictLabel, entryTypeTitle, chartPhase, phaseTitle,
                score, primaryEntry, stopLoss, primaryTarget, riskReward, trailingStop, holdTarget, zoneLow, zoneHigh, headline, newCandidate,
                momentumRank, momentumPct, momentumTop, sector, marketCap, r, dip, fear);
    }

    public static ScreeningCandidate from(ScreeningResult r) {
        return from(r, null, null);
    }

    public static ScreeningCandidate from(ScreeningResult r, MomentumRank m) {
        return from(r, m, null);
    }

    /** m: 같은 종목의 최신 모멘텀 순위 (없으면 null), sector: 종목 섹터 (없으면 null) */
    public static ScreeningCandidate from(ScreeningResult r, MomentumRank m, String sector) {
        return new ScreeningCandidate(
                r.getStockCode(),
                r.getStockName() != null ? r.getStockName() : r.getStockCode(),
                r.getNation(),
                r.isIndexEtf(),
                r.getMarketCapRank(),
                r.getCurrentPrice(),
                r.getVerdict(),
                labelOf(r.getVerdict()),
                r.getEntryType() != null ? r.getEntryType().getTitle() : null,
                r.getChartPhase(),
                r.getChartPhase() != null ? r.getChartPhase().getTitle() : null,
                r.getScore(),
                r.getPrimaryEntry(),
                r.getStopLoss(),
                r.getPrimaryTarget(),
                r.getRiskReward(),
                r.getTrailingStop() != null ? r.getTrailingStop() : 0,
                r.getHoldTarget() != null ? r.getHoldTarget() : 0,
                r.getZoneLow() != null ? r.getZoneLow() : 0,
                r.getZoneHigh() != null ? r.getZoneHigh() : 0,
                r.getHeadline(),
                r.isNewCandidate(),
                m != null ? m.getRankNo() : null,
                m != null ? m.getPct() : null,
                m != null && m.getPct() <= MomentumService.CANDIDATE_PCT,
                sector,
                r.getMarketCap(),
                null,
                false,
                false);
    }

    private static String labelOf(Verdict v) {
        if (v == null) return "";
        return switch (v) {
            case ACTIONABLE -> "기술적 조건 충족";
            case WAIT_TRIGGER -> "양봉 대기";
            case WAIT_FOR_ZONE -> "눌림 대기";
            case CHASE_RISK -> "추격 주의";
            case RR_TOO_LOW -> "손익비 부족";
            case BOUNDARY_WATCH -> "경계 확인 중";
            case NO_SETUP -> "타점 없음";
        };
    }
}
