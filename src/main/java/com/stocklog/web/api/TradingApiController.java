package com.stocklog.web.api;

import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.purchase.trade.dto.request.TradeReviewRequest;
import com.stocklog.modules.purchase.trade.dto.response.TradeReviewResponse;
import com.stocklog.modules.purchase.trade.service.TradeReviewService;
import com.stocklog.global.annotation.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/trade-review")
@RequiredArgsConstructor
public class TradingApiController {

    private final TradeReviewService tradeService;

    // 자유 복기 작성 (POST)
    @PostMapping
    public ResponseEntity<Void> createReview(@LoginUser MemberResponse loginMember, @RequestBody @Valid TradeReviewRequest request) {
        tradeService.saveReview(loginMember.getId(), request);
        return ResponseEntity.ok().build();
    }

    // 월별 복기 피드 조회 (GET)
    @GetMapping
    public ResponseEntity<List<TradeReviewResponse>> getMonthlyReviews(
            @LoginUser MemberResponse loginMember,
            @RequestParam(name = "date") String date
    ) {
        LocalDate targetDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        List<TradeReviewResponse> responses = tradeService.getMonthlyReviews(loginMember.getId(), targetDate);
        return ResponseEntity.ok(responses);
    }

    // 복기 수정
    @PostMapping("/{reviewId}/update")
    public ResponseEntity<Void> updateReview(@LoginUser MemberResponse loginMember, @PathVariable Long reviewId, @RequestBody @Valid TradeReviewRequest request) {
        tradeService.updateReview(loginMember.getId(), reviewId, request);
        return ResponseEntity.ok().build();
    }

    // 복기 삭제
    @PostMapping("/{reviewId}/delete")
    public ResponseEntity<Void> deleteReview(@LoginUser MemberResponse loginMember, @PathVariable Long reviewId) {
        tradeService.deleteReview(loginMember.getId(), reviewId);
        return ResponseEntity.ok().build();
    }
}