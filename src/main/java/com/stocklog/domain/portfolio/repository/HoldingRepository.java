package com.stocklog.domain.portfolio.repository;

import com.stocklog.domain.portfolio.entity.Holding;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    Optional<Holding> findByMemberIdAndStockCode(Long memberId, String stockCode);

    void deleteByMemberIdAndStockCode(Long memberId, String stockCode);

    @EntityGraph(attributePaths = {"stock"})
    List<Holding> findAllByMemberId(Long memberId);




}
