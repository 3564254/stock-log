package com.stocklog.modules.purchase.portfolio.setting;

import com.stocklog.modules.purchase.portfolio.dto.response.HoldingResponse;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.trade.entity.TradeLog;
import com.stocklog.modules.purchase.trade.repository.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 투자 성향·운용 자금 설정과 계좌 상태 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentSettingService {

    private final InvestmentSettingRepository repository;
    private final AccountSnapshotRecorder recorder;
    private final TradeLogRepository tradeLogRepository;

    public record Params(double riskPct, double maxWeight, int slots, double brakeDd, double wideStop, double warnRiskPct, String marketWeak, double trailAtr) {
        static Params of(RiskProfile p) {
            return new Params(p.getRiskPct(), p.getMaxWeight(), p.getSlots(), p.getBrakeDd(), p.getWideStop(), p.getWarnRiskPct(), p.getMarketWeak(), p.getTrailAtr());
        }
    }

    /** accountKnown: 운용 자금을 넣어 계좌 금액을 계산할 수 있음 */
    public record InvestmentStatus(String profile, String profileLabel, Params params, BigDecimal capitalKrw, String capitalSince,
                                   boolean accountKnown, BigDecimal holdingsKrw, BigDecimal realizedKrw, BigDecimal unrealizedKrw,
                                   BigDecimal totalAssetsKrw, BigDecimal peakKrw, double ddPct, boolean brakeOn, int unpricedCount,
                                   Double monthPct, int lossStreak, int recentWins, int recentLosses) {}

    @Transactional(readOnly = true)
    public InvestmentSetting get(Long memberId) {
        return repository.findByMemberId(memberId).orElseGet(() -> new InvestmentSetting(memberId));
    }

    @Transactional
    public void save(Long memberId, RiskProfile profile, BigDecimal capitalKrw, boolean resetPeak) {
        InvestmentSetting s = repository.findByMemberId(memberId).orElseGet(() -> repository.save(new InvestmentSetting(memberId)));
        boolean changed = s.update(profile, capitalKrw);
        if (resetPeak) s.restartFromToday();
        if (changed || resetPeak) recorder.resetPeak(memberId);
    }

    /** 포트폴리오(현재가·원화 평가액 포함)로 계좌 상태를 만든다 */
    public InvestmentStatus status(Long memberId, PortfolioResponse portfolio) {
        InvestmentSetting s = get(memberId);
        RiskProfile p = s.getRiskProfile() != null ? s.getRiskProfile() : RiskProfile.BASE;
        BigDecimal capital = s.getCapitalKrw();
        BigDecimal holdings = portfolio != null ? portfolio.totalMarketValueKrw() : null;
        int holdingCount = portfolio != null && portfolio.myStocks() != null ? portfolio.myStocks().size() : 0;
        int unpriced = holdingCount - (portfolio != null ? portfolio.weightedCount() : 0);

        BigDecimal unreal = unrealizedKrw(portfolio);
        BigDecimal realized = BigDecimal.ZERO;
        boolean known = capital != null;
        if (known && s.getCapitalSince() != null) {
            try {
                BigDecimal r = tradeLogRepository.sumProfitSince(memberId, s.getCapitalSince());
                if (r != null) realized = r;
            } catch (RuntimeException e) {
                log.warn("[투자 설정] 실현 손익 합계 실패: {}", e.getMessage());
            }
        }
        BigDecimal total = known ? capital.add(realized).add(unreal).setScale(0, RoundingMode.HALF_UP) : null, peak = null;
        double dd = 0;
        if (known && total.signum() > 0) {
            try {
                peak = recorder.recordAndPeak(memberId, total, BigDecimal.ZERO);
                dd = peak.signum() > 0 ? total.divide(peak, 6, RoundingMode.HALF_UP).doubleValue() * 100 - 100 : 0;
            } catch (RuntimeException e) {
                log.warn("[투자 설정] 계좌 기록 실패: {}", e.getMessage());
            }
        }
        boolean brake = known && dd <= -p.getBrakeDd() * 100;
        Double monthPct = null;
        if (known && total.signum() > 0) {
            try {
                BigDecimal ms = recorder.monthStart(memberId);
                if (ms != null && ms.signum() > 0) monthPct = total.divide(ms, 6, RoundingMode.HALF_UP).doubleValue() * 100 - 100;
            } catch (RuntimeException e) {
                log.warn("[투자 설정] 월초 계좌 계산 실패: {}", e.getMessage());
            }
        }
        int streak = 0, wins = 0, losses = 0;
        boolean streakOpen = true;
        try {
            for (TradeLog t : tradeLogRepository.findTop10ByMember_IdOrderByTradeDateDescIdDesc(memberId)) {
                if (t.getProfit() == null || t.getProfit().signum() == 0) continue;
                boolean loss = t.getProfit().signum() < 0;
                if (loss) losses++; else wins++;
                if (streakOpen && loss) streak++; else streakOpen = false;
            }
        } catch (RuntimeException e) {
            log.warn("[투자 설정] 최근 매매 기록 조회 실패: {}", e.getMessage());
        }
        return new InvestmentStatus(p.name(), p.getLabel(), Params.of(p), capital,
                s.getCapitalSince() != null ? s.getCapitalSince().toString() : null, known, holdings,
                known ? realized.setScale(0, RoundingMode.HALF_UP) : null, unreal.setScale(0, RoundingMode.HALF_UP),
                total, peak, Math.min(0, dd), brake, Math.max(0, unpriced), monthPct, streak, wins, losses);
    }

    /** 보유 평가손익(원) = Σ 원화 평가액 × (1 − 평단/현재가). 환율은 포트폴리오 비중 계산에 이미 들어 있다 */
    private static BigDecimal unrealizedKrw(PortfolioResponse portfolio) {
        BigDecimal sum = BigDecimal.ZERO;
        if (portfolio == null || portfolio.myStocks() == null || portfolio.totalMarketValueKrw() == null) return sum;
        for (HoldingResponse h : portfolio.myStocks()) {
            if (h.weight() == null || h.currentPrice() == null || h.averagePrice() == null || h.currentPrice().signum() <= 0) continue;
            BigDecimal krw = portfolio.totalMarketValueKrw().multiply(h.weight()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal ratio = BigDecimal.ONE.subtract(h.averagePrice().divide(h.currentPrice(), 8, RoundingMode.HALF_UP));
            sum = sum.add(krw.multiply(ratio));
        }
        return sum;
    }
}
