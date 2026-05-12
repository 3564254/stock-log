package com.stocklog.domain.marketdata.universe.repository;

import com.stocklog.domain.stock.analysis.domain.signal.vo.MarketPhase;
import com.stocklog.domain.marketdata.universe.entity.UniverseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UniverseRepository extends JpaRepository<UniverseStatus, String> {

    // 1. IN 절을 활용하여 여러 국면(Phase)에 해당하는 종목 수를 한 번에 카운트
    long countByPhaseIn(List<MarketPhase> phases);

    // 2. 단일 국면 카운트용 (DANGER_ZONE 조회용)
    long countByPhase(MarketPhase phase);
}
