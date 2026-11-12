package com.stocklog.modules.stock.signal;

import com.stocklog.modules.common.type.Nation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 차트 신호 기록 매일 스크리닝이 분석한 모든 종목을 한 행씩 남긴다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "signal_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_signal_code_date_ver", columnNames = {"stock_code", "signal_date", "engine_version"}),
        indexes = {@Index(name = "idx_signal_status", columnList = "outcome_status"),
                   @Index(name = "idx_signal_nation_date", columnList = "nation, signal_date")})
public class SignalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "signal_record_id")
    private Long id;

    // 신호 당시 (변경 없음)
    @Column(name = "recorded_at", nullable = false) private LocalDateTime recordedAt;   // 분석 시각
    @Column(name = "signal_date", nullable = false) private LocalDate signalDate;       // 분석에 쓴 마지막 일봉 날짜 = 재현용 데이터 기준일
    @Column(name = "stock_code", nullable = false, length = 20) private String stockCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Nation nation;
    @Column(name = "engine_version", nullable = false, length = 40) private String engineVersion;
    @Column(name = "track_version", nullable = false, length = 20) private String trackVersion;

    @Column(length = 20) private String verdict;              // 엔진 판정 (ACTIONABLE 등)
    @Column(name = "entry_type", length = 20) private String entryType;
    @Column(name = "chart_phase", length = 30) private String chartPhase;
    @Column(length = 200) private String headline;            // 진입 근거 요약 (엔진 서술)

    @Column(name = "close_px") private double close;          // 신호일 종가
    @Column private double atr;                                // 신호일 ATR(14)
    @Column private Double ma20;
    @Column(name = "ma40w") private Double ma40w;              // 40주선
    @Column(name = "trend_up") private Boolean trendUp;        // 종가 > 40주선 AND 40주선 > 4주 전 40주선
    @Column(name = "ret126") private Double ret126;            // 종목 126거래일 수익
    @Column(name = "index_ret126") private Double indexRet126; // 같은 기간 지수
    @Column(name = "market_weak") private Boolean marketWeak;  // 지수가 40주선 아래
    @Column(name = "momentum_pct") private Double momentumPct; // 미국 12-1 모멘텀 상위 %, 없으면 null

    @Column(name = "plan_entry") private double planEntry;
    @Column(name = "plan_stop") private double planStop;
    @Column(name = "plan_target") private double planTarget;
    @Column(name = "risk_reward") private double riskReward;
    @Column(name = "trade_risk_pct") private Double tradeRiskPct;   // 진입가 → 손절가 손실 %
    @Column(name = "rec_weight") private Double recWeight;          // 위험 1% 기준 권장 비중 %

    // 이후 결과 (가상 추적, 나중에 채움)
    @Column(name = "outcome_status", nullable = false, length = 10) private String outcomeStatus = "PENDING";  // PENDING · PARTIAL · DONE · NODATA
    @Column(name = "entry_date") private LocalDate entryDate;
    @Column(name = "entry_open") private Double entryOpen;
    @Column private Double ret5;
    @Column private Double ret10;
    @Column private Double ret20;
    @Column private Double mfe20;
    @Column private Double mae20;
    @Column(name = "stop_hit20") private Boolean stopHit20;
    @Column(name = "target_hit20") private Boolean targetHit20;
    @Column(name = "first_hit", length = 10) private String firstHit;  // STOP · TARGET · NONE
    @Column(name = "x10_net") private Double x10Net;
    @Column(name = "x20_net") private Double x20Net;
    @Column(name = "trail_net") private Double trailNet;
    @Column(name = "trail_days") private Integer trailDays;
    @Column(name = "evaluated_at") private LocalDateTime evaluatedAt;

    @Builder
    public SignalRecord(LocalDateTime recordedAt, LocalDate signalDate, String stockCode, Nation nation,
                        String verdict, String entryType, String chartPhase, String headline,
                        double close, double atr, Double ma20, Double ma40w, Boolean trendUp, Double ret126, Double indexRet126,
                        Boolean marketWeak, Double momentumPct, double planEntry, double planStop, double planTarget,
                        double riskReward, Double tradeRiskPct, Double recWeight) {
        this.recordedAt = recordedAt; this.signalDate = signalDate; this.stockCode = stockCode; this.nation = nation;
        this.engineVersion = SignalVersion.ENGINE; this.trackVersion = SignalVersion.TRACK;
        this.verdict = verdict; this.entryType = entryType; this.chartPhase = chartPhase; this.headline = headline;
        this.close = close; this.atr = atr; this.ma20 = ma20; this.ma40w = ma40w; this.trendUp = trendUp;
        this.ret126 = ret126; this.indexRet126 = indexRet126; this.marketWeak = marketWeak; this.momentumPct = momentumPct;
        this.planEntry = planEntry; this.planStop = planStop; this.planTarget = planTarget; this.riskReward = riskReward;
        this.tradeRiskPct = tradeRiskPct; this.recWeight = recWeight;
    }

    public boolean isSignal() { return "ACTIONABLE".equals(verdict); }

    /** 결과 칸만 갱신한다 */
    public void applyOutcome(SignalOutcomes.Outcome o, LocalDateTime now) {
        this.entryDate = o.entryDate(); this.entryOpen = o.entryOpen();
        this.ret5 = o.ret5(); this.ret10 = o.ret10(); this.ret20 = o.ret20();
        this.mfe20 = o.mfe20(); this.mae20 = o.mae20();
        this.stopHit20 = o.stopHit20(); this.targetHit20 = o.targetHit20(); this.firstHit = o.firstHit();
        this.x10Net = o.x10Net(); this.x20Net = o.x20Net(); this.trailNet = o.trailNet(); this.trailDays = o.trailDays();
        this.outcomeStatus = o.status(); this.evaluatedAt = now;
    }
}
