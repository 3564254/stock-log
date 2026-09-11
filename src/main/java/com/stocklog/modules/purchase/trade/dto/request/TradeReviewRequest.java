package com.stocklog.modules.purchase.trade.dto.request;

import com.stocklog.modules.purchase.trade.type.TradeEmotion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TradeReviewRequest(
        // 특정 매매나 종목 연결은 필수가 아닌 선택 사항으로 변경
        Long tradeLogId,
        String stockCode,

        @NotBlank(message = "제목은 간단히 입력해주세요")
        String title,

        @NotBlank(message = "내용을 입력해주세요")
        String content,

        // 감정 상태나 평점도 비워둘 수 있도록 설정
        TradeEmotion emotion,

        @Min(value = 1, message = "평점은 1점 이상이어야 합니다")
        @Max(value = 5, message = "평점은 5점 이하이어야 합니다")
        Integer score
) {
}