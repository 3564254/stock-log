package com.stocklog.modules.purchase.portfolio.setting;

import com.stocklog.modules.member.application.usecase.GetMemberUseCase;
import com.stocklog.modules.purchase.portfolio.entity.PortfolioSnapshot;
import com.stocklog.modules.purchase.portfolio.repository.PortfolioSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 계좌 총자산(보유 원화 평가액 + 현금)을 하루 1건 기록하고 고점을 돌려준다 */
@Service
@RequiredArgsConstructor
public class AccountSnapshotRecorder {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final GetMemberUseCase memberUseCase;

    /** 오늘 값을 기록(있으면 갱신)하고, 오늘 포함 기록된 총자산의 최고값을 돌려준다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal recordAndPeak(Long memberId, BigDecimal holdingsKrw, BigDecimal cashKrw) {
        LocalDate today = LocalDate.now();
        PortfolioSnapshot last = snapshotRepository.findTopByMemberIdOrderByCreatedDateDesc(memberId).orElse(null);
        if (last != null && last.getCreatedDate() != null && last.getCreatedDate().toLocalDate().equals(today)) {
            last.refresh(holdingsKrw, cashKrw);
        } else {
            snapshotRepository.save(PortfolioSnapshot.builder()
                    .member(memberUseCase.getMemberReference(memberId))
                    .totalEvaluationAmount(holdingsKrw)
                    .cashBalance(cashKrw)
                    .build());
        }
        BigDecimal now = holdingsKrw.add(cashKrw != null ? cashKrw : BigDecimal.ZERO);
        BigDecimal peak = now;
        List<PortfolioSnapshot> all = snapshotRepository.findAllByMemberIdOrderByCreatedDateAsc(memberId);
        for (PortfolioSnapshot s : all) {
            if (s.getTotalEvaluationAmount() == null) continue;
            BigDecimal v = s.calculateTotalAssets();
            if (v.compareTo(peak) > 0) peak = v;
        }
        return peak;
    }

    /** 이번 달 시작 계좌 금액: 지난달 마지막 기록, 없으면 이번 달 첫 기록. 기록이 없으면 null */
    @Transactional(readOnly = true)
    public BigDecimal monthStart(Long memberId) {
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        BigDecimal before = null, inMonth = null;
        for (PortfolioSnapshot s : snapshotRepository.findAllByMemberIdOrderByCreatedDateAsc(memberId)) {
            if (s.getTotalEvaluationAmount() == null || s.getCreatedDate() == null) continue;
            LocalDate d = s.getCreatedDate().toLocalDate();
            if (d.isBefore(first)) before = s.calculateTotalAssets();
            else if (inMonth == null) inMonth = s.calculateTotalAssets();
        }
        return before != null ? before : inMonth;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetPeak(Long memberId) {
        snapshotRepository.deleteAllByMemberId(memberId);
    }
}
