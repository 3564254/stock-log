package com.stocklog.modules.purchase.trade.service;

import com.stocklog.modules.purchase.trade.dto.request.TradeReviewRequest;
import com.stocklog.modules.purchase.trade.dto.response.TradeReviewResponse;
import com.stocklog.modules.purchase.trade.entity.TradeReview;
import com.stocklog.modules.purchase.trade.repository.TradeReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeReviewService {

    private final TradeReviewRepository tradeReviewRepository;

    /**
     * 특정 월의 자유 복기 노트 목록 조회 (회원 ID 기반 격리)
     */
    public List<TradeReviewResponse> getMonthlyReviews(Long memberId, LocalDate targetDate) {
        LocalDateTime startDateTime = targetDate.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endDateTime = targetDate.withDayOfMonth(targetDate.lengthOfMonth()).atTime(23, 59, 59);

        // 해당 회원의 해당 월 데이터만 안전하게 필터링하여 가져온 후 DTO로 매핑
        return tradeReviewRepository.findAllByMemberIdAndCreatedAtBetweenOrderByCreatedAtDesc(memberId, startDateTime, endDateTime)
                .stream()
                .map(TradeReviewResponse::from)
                .toList();
    }

    /**
     * 자유 복기 노트 작성
     */
    @Transactional
    public void saveReview(Long memberId, TradeReviewRequest request) {
        tradeReviewRepository.save(TradeReview.builder()
                .memberId(memberId) // ✨ 작성한 회원의 고유 ID 바인딩
                .tradeLogId(request.tradeLogId()) // 선택 항목 (Null 허용)
                .stockCode(request.stockCode())   // 선택 항목 (Null 허용)
                .title(request.title())
                .content(request.content())
                .emotion(request.emotion())
                .score(request.score())
                .createdAt(LocalDateTime.now())
                .build()
        );
    }

    /**
     * 복기 노트 수정
     */
    @Transactional
    public void updateReview(Long memberId, Long reviewId, TradeReviewRequest request) {
        // 1. 메모 조회 및 존재성 체크
        TradeReview review = tradeReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 복기 노트입니다."));

        // 2. ✨ 소유권 검증 (메모의 작성자와 요청한 사용자가 일치하는지 체크)
        validateReviewOwnership(memberId, review);

        // 3. 수정 반영 (더티 체킹)
        review.update(
                request.title(),
                request.content(),
                request.emotion(),
                request.score()
        );
    }

    /**
     * 복기 노트 삭제
     */
    @Transactional
    public void deleteReview(Long memberId, Long reviewId) {
        TradeReview review = tradeReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 복기 노트입니다."));

        // ✨ 소유권 검증
        validateReviewOwnership(memberId, review);

        tradeReviewRepository.delete(review);
    }

    // ================= 내부 헬퍼 메서드 =================

    /**
     * 복기 노트를 조작할 수 있는 정당한 권한이 있는지 검증
     */
    private void validateReviewOwnership(Long memberId, TradeReview review) {
        if (!review.getMemberId().equals(memberId)) {
            throw new IllegalArgumentException("해당 복기 노트에 접근할 권한이 없습니다.");
        }
    }
}