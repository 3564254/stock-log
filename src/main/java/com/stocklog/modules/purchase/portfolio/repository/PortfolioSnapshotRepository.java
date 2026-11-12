package com.stocklog.modules.purchase.portfolio.repository;

import com.stocklog.modules.purchase.portfolio.entity.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

    // 유저의 가장 최근 포트폴리오 스냅샷 1건 조회
    Optional<PortfolioSnapshot> findTopByMemberIdOrderByCreatedDateDesc(Long memberId);

    // 유저의 전체 스냅샷 시계열 조회
    List<PortfolioSnapshot> findAllByMemberIdOrderByCreatedDateAsc(Long memberId);

    // 계좌 고점 초기화
    void deleteAllByMemberId(Long memberId);
}
