package com.stocklog.modules.purchase.portfolio.repository;

import com.stocklog.modules.purchase.portfolio.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

    // 1. 유저의 가장 최근 포트폴리오 스냅샷 1건 조회 (메인 대시보드 자산 배분 렌더링용)
    Optional<PortfolioSnapshot> findTopByMemberIdOrderByCreatedDateDesc(Long memberId);

    // 2. 유저의 전체 스냅샷 시계열 조회 (추후 자산 성장 그래프 시각화용)
    List<PortfolioSnapshot> findAllByMemberIdOrderByCreatedDateAsc(Long memberId);
}