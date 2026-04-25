package com.stocklog.domain.tradelog.repository;

import com.stocklog.domain.member.entity.Member;
import com.stocklog.domain.tradelog.entity.TradeLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {

    @Query("SELECT t FROM TradeLog t WHERE t.member = :member AND t.tradeDate >= :startDate AND t.tradeDate < :endDate ORDER BY t.tradeDate")
    List<TradeLog> findMonthlyTradeLogs(Member member, LocalDate startDate, LocalDate endDate, Sort sort);
}
