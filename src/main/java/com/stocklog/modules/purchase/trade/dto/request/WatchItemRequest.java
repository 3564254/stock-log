package com.stocklog.modules.purchase.trade.dto.request;
import jakarta.validation.constraints.NotBlank;

public record WatchItemRequest(
        @NotBlank(message = "종목 코드는 필수 항목임")
        String stockCode
) {
}
