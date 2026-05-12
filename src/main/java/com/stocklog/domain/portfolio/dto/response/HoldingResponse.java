package com.stocklog.domain.portfolio.dto.response;

import com.stocklog.domain.portfolio.entity.Holding;
import com.stocklog.domain.stock.common.type.Currency;
import lombok.Builder;
import com.stocklog.global.util.FinancialConst;

import java.math.BigDecimal;

@Builder
public record HoldingResponse(
        Long memberId,
        String stockCode,
        String korName,
        String sector,
        BigDecimal quantity,
        Currency currency,
        BigDecimal purchaseExchangeRate,
        BigDecimal currentExchangeRate,
        BigDecimal currentPrice,
        BigDecimal purchasePrice,
        BigDecimal profit,
        BigDecimal profitRate
) {
    public static HoldingResponse of(Holding holding, BigDecimal currentPrice, BigDecimal currentExchangeRate) {
        return HoldingResponse.builder()
                .memberId(holding.getMember().getId())
                .stockCode(holding.getStock().getCode())
                .korName(holding.getStock().getName())
                .sector(holding.getStock().getSector())
                .quantity(holding.getQuantity())
                .currency(holding.getCurrency())
                .purchaseExchangeRate(holding.getPurchaseExchangeRate())
                .currentExchangeRate(currentExchangeRate)
                .currentPrice(currentPrice)
                .purchasePrice(holding.getPurchasePrice())
                .profit(holding.calculateProfit(currentPrice, currentExchangeRate))
                .profitRate(holding.calculateProfitRate(currentPrice, currentExchangeRate))
                .build();
    }

    public static HoldingResponse createFallback() {
        return HoldingResponse.builder()
                .memberId(0L)
                .stockCode("")
                .korName("")
                .sector("")
                .quantity(BigDecimal.ZERO)
                .currency(Currency.UNKNOWN)
                .purchaseExchangeRate(BigDecimal.ZERO)
                .currentExchangeRate(BigDecimal.ZERO)
                .currentPrice(BigDecimal.ZERO)
                .purchasePrice(BigDecimal.ZERO)
                .profit(BigDecimal.ZERO)
                .profitRate(BigDecimal.ZERO)
                .build();
    }

    public BigDecimal getCurrentValueKrw() {
        return this.currentPrice().multiply(this.currentExchangeRate()).multiply(this.quantity());
    }

    public BigDecimal getPurchaseValueKrw() {
        return this.purchasePrice().multiply(this.purchaseExchangeRate()).multiply(this.quantity());
    }
}