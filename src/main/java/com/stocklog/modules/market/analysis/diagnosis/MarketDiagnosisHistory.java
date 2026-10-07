package com.stocklog.modules.market.analysis.diagnosis;

import com.stocklog.modules.market.domain.type.MacroRegimeType;
import com.stocklog.modules.market.domain.type.MarketRegimeType;
import com.stocklog.modules.market.domain.type.RiskAppetiteType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "market_diagnosis_history",
        indexes = {
                @Index(name = "idx_history_date", columnList = "date", unique = true),
                @Index(name = "idx_history_regime", columnList = "regime"),
                @Index(name = "idx_history_macro_narrative", columnList = "macro_narrative")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketDiagnosisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    // ── [1] 핵심 시계열 지표 ──
    @Column(nullable = false)
    private double compositeScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketRegimeType regime;

    @Column(nullable = false)
    private int regimeStreakDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskAppetiteType risk;

    @Column(nullable = false)
    private int confidence;

    // ── [2] 세부 분석기별 시계열 점수 (다중 라인 차트 렌더링 및 통계용) ──
    @Column(nullable = false)
    private double trendScore;       // MarketTrend

    @Column(nullable = false)
    private double momentumScore;    // MarketMomentum

    @Column(nullable = false)
    private double breadthScore;     // MarketBreadth (시장 건전성 및 왜곡 감지)

    @Column(nullable = false)
    private double ratesScore;       // InterestRates

    @Column(nullable = false)
    private double liquidityScore;   // Liquidity

    @Column(nullable = false)
    private double macroScore;       // MacroCycle

    @Column(nullable = false)
    private double creditScore;      // CreditHealth

    @Column(nullable = false)
    private double sectorScore;      // SectorRotation

    @Column(nullable = false)
    private double defensiveScore;   // DefensiveRotation

    @Column(nullable = false)
    private double crossAssetScore;  // CrossAsset

    // ── [3] 거시 국면 및 경고 플래그 ──
    @Enumerated(EnumType.STRING)
    @Column(name = "macro_regime", length = 30)
    private MacroRegimeType macroRegime;

    @Column(name = "macro_narrative", length = 30)
    private String macroNarrative;   // 예: "STAGFLATION_FEAR"

    @Column(nullable = false)
    private boolean hasBreadthDivergence; // 대형주 쏠림 등 시장 너비 괴리 여부

    // ── [4] 세부 분석 원본 데이터 (필요시 백테스팅/디버깅용) ──
    @Column(columnDefinition = "LONGTEXT") // JSON 크기를 고려해 TEXT -> LONGTEXT 권장
    private String rawEngineInputJson;

    @Builder
    public MarketDiagnosisHistory(LocalDate date, double compositeScore, MarketRegimeType regime,
                                  int regimeStreakDays, RiskAppetiteType risk, int confidence,
                                  double trendScore, double momentumScore, double breadthScore,
                                  double ratesScore, double liquidityScore, double macroScore,
                                  double creditScore, double sectorScore, double defensiveScore, double crossAssetScore,
                                  MacroRegimeType macroRegime, String macroNarrative, boolean hasBreadthDivergence,
                                  String rawEngineInputJson) {
        this.date = date;
        this.compositeScore = compositeScore;
        this.regime = regime;
        this.regimeStreakDays = regimeStreakDays;
        this.risk = risk;
        this.confidence = confidence;
        this.trendScore = trendScore;
        this.momentumScore = momentumScore;
        this.breadthScore = breadthScore;
        this.ratesScore = ratesScore;
        this.liquidityScore = liquidityScore;
        this.macroScore = macroScore;
        this.creditScore = creditScore;
        this.sectorScore = sectorScore;
        this.defensiveScore = defensiveScore;
        this.crossAssetScore = crossAssetScore;
        this.macroRegime = macroRegime;
        this.macroNarrative = macroNarrative;
        this.hasBreadthDivergence = hasBreadthDivergence;
        this.rawEngineInputJson = rawEngineInputJson;
    }
}