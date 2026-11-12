package com.stocklog.modules.stock.screening.entity;

import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.analysis.signal.trading.EntryType;
import com.stocklog.modules.stock.analysis.signal.trading.Verdict;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 시가총액 상위 종목을 차트 기준으로 분석한 결과 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "screening_result", indexes = {
        @Index(name = "idx_screening_nation", columnList = "nation")
})
public class ScreeningResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_result_id")
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "stock_name", length = 100)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Nation nation;

    /** 이 컬럼이 생기기 전 행은 null → 개별 종목으로 본다 (getCategory) */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ScreeningCategory category;

    @Column(name = "market_cap_rank", nullable = false)
    private int marketCapRank;              // 분석 대상 안에서의 시가총액 순위 (1부터), 지수 ETF는 0

    @Column(name = "market_cap", precision = 24, scale = 2)
    private BigDecimal marketCap;           // 원화 기준

    @Column(name = "current_price", precision = 20, scale = 4)
    private BigDecimal currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "chart_phase", length = 30)
    private ChartPhase chartPhase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Verdict verdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 20)
    private EntryType entryType;

    @Column(nullable = false)
    private int score;                      // 모멘텀(30) + 기술(30) = 0~60

    @Column(name = "momentum_score", nullable = false)
    private int momentumScore;

    @Column(name = "technical_score", nullable = false)
    private int technicalScore;

    @Column(name = "primary_entry")
    private double primaryEntry;

    @Column(name = "stop_loss")
    private double stopLoss;

    @Column(name = "primary_target")
    private double primaryTarget;

    @Column(name = "risk_reward")
    private double riskReward;              // 0 이면 산출 불가

    /** 보유 관점 손절선. 컬럼 추가 전 행은 null */
    @Column(name = "trailing_stop")
    private Double trailingStop;

    /** 보유 관점 익절 목표: 현재가 위 가장 가까운 저항 (없으면 0) */
    @Column(name = "hold_target")
    private Double holdTarget;

    /** 진입 구간 하단·상단. 홈 카드에서 실시간 가격이 구간 안/위/아래인지 다시 계산할 때 쓴다 */
    @Column(name = "zone_low")
    private Double zoneLow;

    @Column(name = "zone_high")
    private Double zoneHigh;

    @Column(length = 200)
    private String headline;                // 타점 엔진의 상황 요약 한 줄

    /** 진단용: 진입 계획 관문 중 어디서 멈췄는지 */
    @Enumerated(EnumType.STRING)
    @Column(name = "reject_stage", length = 20)
    private ScreeningStage rejectStage;

    /** 진단용: 국면 관문을 무시하고 진입 방식 조건만 봤을 때의 유형 */
    @Enumerated(EnumType.STRING)
    @Column(name = "raw_entry_type", length = 20)
    private EntryType rawEntryType;

    /** 직전 분석 때는 진입 후보가 아니었는데 이번에 새로 들어왔는지 */
    @Column(name = "new_candidate")
    private Boolean newCandidate;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @Builder
    public ScreeningResult(String stockCode, String stockName, Nation nation, ScreeningCategory category, int marketCapRank, BigDecimal marketCap,
                           BigDecimal currentPrice, ChartPhase chartPhase, Verdict verdict, EntryType entryType,
                           int momentumScore, int technicalScore, double primaryEntry, double stopLoss,
                           double primaryTarget, double riskReward, Double trailingStop, Double holdTarget,
                           Double zoneLow, Double zoneHigh,
                           String headline, boolean newCandidate, ScreeningStage rejectStage, EntryType rawEntryType,
                           LocalDateTime analyzedAt) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.nation = nation;
        this.category = category;
        this.marketCapRank = marketCapRank;
        this.marketCap = marketCap;
        this.currentPrice = currentPrice;
        this.chartPhase = chartPhase;
        this.verdict = verdict;
        this.entryType = entryType;
        this.momentumScore = momentumScore;
        this.technicalScore = technicalScore;
        this.score = momentumScore + technicalScore;
        this.primaryEntry = primaryEntry;
        this.stopLoss = stopLoss;
        this.primaryTarget = primaryTarget;
        this.riskReward = riskReward;
        this.trailingStop = trailingStop;
        this.holdTarget = holdTarget;
        this.zoneLow = zoneLow;
        this.zoneHigh = zoneHigh;
        this.headline = headline;
        this.newCandidate = newCandidate;
        this.rejectStage = rejectStage;
        this.rawEntryType = rawEntryType;
        this.analyzedAt = analyzedAt;
    }

    public ScreeningCategory getCategory() {
        return category != null ? category : ScreeningCategory.STOCK;
    }

    public boolean isNewCandidate() {
        return Boolean.TRUE.equals(newCandidate);
    }

    public boolean isIndexEtf() {
        return getCategory() == ScreeningCategory.INDEX_ETF;
    }
}
