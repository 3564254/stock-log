package com.stocklog.domain.tradelog.dto.request;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;


@Builder
public record TradeLogRequest (
        String stockCode,
        BigDecimal quantity,
        BigDecimal profit,
        LocalDate tradeDate
) {

}