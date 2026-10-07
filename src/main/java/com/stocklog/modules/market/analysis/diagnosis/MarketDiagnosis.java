package com.stocklog.modules.market.analysis.diagnosis;

import com.stocklog.modules.market.domain.type.MarketRegimeType;
import com.stocklog.modules.market.domain.type.RiskAppetiteType;
import com.stocklog.modules.market.domain.type.TrendDirection;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(
        name = "market_diagnosis",
        indexes = @Index(name = "idx_market_diagnosis_date", columnList = "date", unique = true)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    // ── [1] 수치화된 메인 스코어 및 추세 ──
    @Column(nullable = false)
    private double compositeScore;

    @Column(nullable = false)
    private double scoreChange1D;

    @Column(nullable = false)
    private double scoreChange5D;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrendDirection trendDirection;

    @Column(nullable = false)
    private int regimeStreakDays;

    // ── [2] 핵심 국면 판단 (Enum 적용) ──
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketRegimeType regime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskAppetiteType risk;

    @Column(nullable = false, length = 100)
    private String regimeDescription;

    @Column(nullable = false)
    private int confidence;

    // ── [3] 서브 그룹 점수 (상세 분석용) ──
    private double marketPriceScore;
    private double macroLiquidityScore;

    @Column(nullable = false)
    private double sectorScore; // [추가] 섹터 로테이션 스코어

    private double crossAssetScore;

    // ── [4] AI 종합 코멘터리 ──
    @Column(columnDefinition = "TEXT", nullable = false)
    private String comment;

    @Builder
    public MarketDiagnosis(LocalDate date, double compositeScore, double scoreChange1D, double scoreChange5D,
                           TrendDirection trendDirection, int regimeStreakDays, MarketRegimeType regime,
                           RiskAppetiteType risk, String regimeDescription, int confidence,
                           double marketPriceScore, double macroLiquidityScore, double sectorScore, double crossAssetScore, String comment) {
        this.date = date;
        this.compositeScore = compositeScore;
        this.scoreChange1D = scoreChange1D;
        this.scoreChange5D = scoreChange5D;
        this.trendDirection = trendDirection;
        this.regimeStreakDays = regimeStreakDays;
        this.regime = regime;
        this.risk = risk;
        this.regimeDescription = regimeDescription;
        this.confidence = confidence;
        this.marketPriceScore = marketPriceScore;
        this.macroLiquidityScore = macroLiquidityScore;
        this.sectorScore = sectorScore; // [추가] 생성자 매핑
        this.crossAssetScore = crossAssetScore;
        this.comment = comment;
    }

    public void updateComment(String newComment) {
        this.comment = newComment;
    }

    public void updateconfidence(String newConfidence) {
        this.comment = newConfidence;
    }
}