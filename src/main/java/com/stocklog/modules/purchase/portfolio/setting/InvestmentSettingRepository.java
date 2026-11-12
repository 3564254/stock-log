package com.stocklog.modules.purchase.portfolio.setting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvestmentSettingRepository extends JpaRepository<InvestmentSetting, Long> {
    Optional<InvestmentSetting> findByMemberId(Long memberId);
}
