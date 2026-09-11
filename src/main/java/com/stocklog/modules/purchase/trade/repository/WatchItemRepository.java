package com.stocklog.modules.purchase.trade.repository;

import com.stocklog.modules.purchase.trade.entity.WatchItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchItemRepository extends JpaRepository<WatchItem, Long> {

    // 1. 특정 회원의 관심 종목 전체 목록 조회 (메인 대시보드 렌더링용)
    List<WatchItem> findAllByMemberId(Long memberId);

    // 2. 특정 회원의 특정 종목 단건 조회 (상세 데이터 검증용)
    Optional<WatchItem> findByMemberIdAndStockCode(Long memberId, String stockCode);

    // 3. 관심 종목 삭제 (대기 큐에서 제거)
    void deleteByMemberIdAndStockCode(Long memberId, String stockCode);

    // 4. 관심 종목 중복 여부 확인 (신규 추가 시 검증용 최적화 쿼리)
    boolean existsByMemberIdAndStockCode(Long memberId, String stockCode);
}