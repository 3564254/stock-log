package com.stocklog.modules.stock.analysis.signal.trading;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.type.ChartPhase;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public record TradingSignalResult(
        String symbol,
        SignalStrength strength,
        ChartAnalysis analysis,
        EntryPlan entryPlan,
        Narrative narrative,
        PriceTarget priceTarget,
        RiskManagement risk,
        String verdict,
        LocalDateTime timestamp
) {

    // --- 하위 중첩 레코드 정의 ---

    public record SignalStrength(
            List<String> buySignals,
            List<String> sellSignals,
            List<String> watchSignals,
            int buyStrength,
            int sellStrength
    ) {
        public static SignalStrength empty() {
            return new SignalStrength(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 0, 0);
        }
    }

    public record ChartAnalysis(
            ChartPhase phase,
            MarketContext marketContext
    ) {
        public static ChartAnalysis unknown() {
            return new ChartAnalysis(ChartPhase.INSUFFICIENT_DATA, MarketContext.createFallback());
        }
    }

    /** 진입 계획 요약. type이 NONE이면 나머지 값은 0이다. riskReward 0.0은 "산출 불가". */
    public record EntryPlan(
            EntryType type,
            double zoneLow,
            double zoneHigh,
            double zoneStrength,
            double riskReward,
            boolean triggerConfirmed
    ) {
        public static EntryPlan none() {
            return new EntryPlan(EntryType.NONE, 0, 0, 0, 0, false);
        }
    }

    /**
     * 화면에 보여줄 상황 설명. 진입 계획이 없어도 항상 채워진다.
     * tone: bull / bear / warn / neutral (대응 안내 제목의 색상 구분용)
     */
    public record Narrative(
            String headline,
            String summary,
            List<Detail> details,
            String actionTitle,
            String actionBody,
            String tone
    ) {
        public record Detail(String icon, String label, String text) {}

        public static Narrative empty() {
            return new Narrative(
                    "분석 데이터가 아직 부족합니다",
                    "일봉 데이터가 충분히 쌓이면 국면 분석과 대응 안내가 표시됩니다.",
                    Collections.emptyList(),
                    "⏳ 분석 대기",
                    "차트 데이터가 충분하지 않아 명확한 대응 안내를 만들기 어렵습니다.",
                    "neutral");
        }
    }

    public record PriceTarget(
            double primaryEntry,
            double secondaryEntry,
            double stopLoss,
            double primaryTarget,
            double secondaryTarget,
            double trailingStop
    ) {
        public static PriceTarget empty() {
            return new PriceTarget(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public record RiskManagement(
            double riskPerShare,
            double tradeRiskPercent,
            double recommendedWeight
    ) {
        public static RiskManagement zero() {
            return new RiskManagement(0.0, 0.0, 0.0);
        }
    }

    public static TradingSignalResult createFallback(String symbol, String reason) {
        return new TradingSignalResult(
                symbol != null ? symbol : "UNKNOWN",
                SignalStrength.empty(),
                ChartAnalysis.unknown(),
                EntryPlan.none(),
                Narrative.empty(),
                PriceTarget.empty(),
                RiskManagement.zero(),
                "FALLBACK: " + reason,
                LocalDateTime.now()
        );
    }

    public static TradingSignalResult createFallback(String symbol) {
        return createFallback(symbol, "DATA_UNAVAILABLE");
    }

    public static TradingSignalResult createFallback() {
        return createFallback("UNKNOWN", "DATA_UNAVAILABLE");
    }


}