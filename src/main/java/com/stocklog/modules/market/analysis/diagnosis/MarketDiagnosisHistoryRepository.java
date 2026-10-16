package com.stocklog.modules.market.analysis.diagnosis;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketDiagnosisHistoryRepository extends JpaRepository<MarketDiagnosisHistory, Long> {

    // 가장 최근 날짜의 히스토리 1건을 조회 (전일 대비 점수 변화량 계산용)
    Optional<MarketDiagnosisHistory> findTopByOrderByDateDesc();

    List<MarketDiagnosisHistory> findAllByOrderByDateDesc(Pageable pageable);

    Optional<MarketDiagnosisHistory> findByDate(java.time.LocalDate date);

    // 시간축 판정의 이전 상태 계산용: 특정 날짜 이전 기록을 최신순으로 최대 120건
    List<MarketDiagnosisHistory> findTop120ByDateBeforeOrderByDateDesc(java.time.LocalDate date);
}