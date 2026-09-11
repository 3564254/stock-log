package com.stocklog.modules.purchase.trade.repository;

import com.stocklog.modules.purchase.trade.entity.TradingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradingRuleRepository extends JpaRepository<TradingRule, Long> {
    List<TradingRule> findAllByMemberIdAndIsActiveTrue(Long memberId);

}
