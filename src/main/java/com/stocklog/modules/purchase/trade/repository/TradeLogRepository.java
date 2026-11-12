package com.stocklog.modules.purchase.trade.repository;

import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.purchase.trade.entity.TradeLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {

    @Query("SELECT t FROM TradeLog t WHERE t.member = :member AND t.tradeDate >= :startDate AND t.tradeDate < :endDate ORDER BY t.tradeDate")
    List<TradeLog> findMonthlyTradeLogs(Member member, LocalDate startDate, LocalDate endDate, Sort sort);

    /** 운용 자금 기준일 이후 기록한 실현 손익 합계(원). 계좌 낙폭 브레이크 계산용 */
    @Query("SELECT COALESCE(SUM(t.profit), 0) FROM TradeLog t WHERE t.member.id = :memberId AND t.tradeDate >= :since")
    java.math.BigDecimal sumProfitSince(Long memberId, LocalDate since);

    /** 최근 매매 기록 10건(최신순). 연속 손실·최근 승패 안내용 */
    List<TradeLog> findTop10ByMember_IdOrderByTradeDateDescIdDesc(Long memberId);
}
