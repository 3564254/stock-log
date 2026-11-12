package com.stocklog.application.dashboard;

import com.stocklog.modules.common.type.ChartPhase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 홈 상단 지수 띠: 시장 국면 → 미국 지수 → 국내 지수 → 환율 순 (탑다운) */
public record MarketStripResponse(
        Regime regime,
        List<Item> items,
        LocalDateTime updatedAt
) {

    /** 시장 분석 엔진(11개 분석기) 판정 요약 */
    public record Regime(
            String regime,          // BULL / BEAR / NEUTRAL
            String risk,            // RISK_ON / RISK_OFF / NEUTRAL
            String trendLevel,      // STRONG_POSITIVE 등
            String description,     // 예: 강한 상승장
            int confidence          // 0~100
    ) {
    }

    /** 지수 한 칸 */
    public record Item(
            String key,
            String name,
            String sub,             // 작은 글씨: ETF 코드 등
            BigDecimal value,
            BigDecimal changeRate,  // %, 모르면 null
            String direction,       // UP / DOWN / FLAT
            String unit,
            List<BigDecimal> spark, // 최근 종가 (오래된 → 최근), 없으면 빈 목록
            LocalDate asOf,
            String etfCode,
            ChartPhase phase,
            String verdict,
            String verdictLabel,
            String link             // 누르면 갈 곳, 없으면 null
    ) {
    }
}
