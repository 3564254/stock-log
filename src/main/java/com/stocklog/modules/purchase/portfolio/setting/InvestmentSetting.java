package com.stocklog.modules.purchase.portfolio.setting;

import com.stocklog.global.common.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 회원별 투자 설정: 성향 + 운용 자금. 입금·출금할 때만 바꾼다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "investment_setting", uniqueConstraints = @UniqueConstraint(name = "uk_investment_setting_member", columnNames = "member_id"))
public class InvestmentSetting extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_profile", length = 20, nullable = false)
    private RiskProfile riskProfile = RiskProfile.BASE;

    // 운용 자금(원). null이면 입력 안 함 → 비중·계좌 위험을 보유 종목 합계 기준으로 계산
    @Column(name = "capital_krw", precision = 18, scale = 0)
    private BigDecimal capitalKrw;

    // 운용 자금을 정한(바꾼) 날. 이날 이후 실현 손익만 계좌에 더하고, 계좌 고점도 이날부터 다시 잡는다
    @Column(name = "capital_since")
    private LocalDate capitalSince;

    public InvestmentSetting(Long memberId) { this.memberId = memberId; }

    public boolean update(RiskProfile profile, BigDecimal capitalKrw) {
        this.riskProfile = profile != null ? profile : RiskProfile.BASE;
        BigDecimal next = capitalKrw != null && capitalKrw.signum() > 0 ? capitalKrw : null;
        boolean changed = (next == null) != (this.capitalKrw == null) || (next != null && next.compareTo(this.capitalKrw) != 0);
        this.capitalKrw = next;
        if (changed) this.capitalSince = next != null ? LocalDate.now() : null;
        return changed;
    }

    public void restartFromToday() { if (capitalKrw != null) this.capitalSince = LocalDate.now(); }
}
