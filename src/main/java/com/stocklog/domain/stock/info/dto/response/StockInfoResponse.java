package com.stocklog.domain.stock.info.dto.response;

import com.stocklog.domain.stock.common.type.AssetType;
import com.stocklog.domain.stock.common.type.MarketType;
import com.stocklog.domain.stock.common.type.TradingStatus;
import lombok.Builder;
import com.stocklog.global.util.FinancialConst;

import java.math.BigDecimal;

@Builder
public record StockInfoResponse(
        String code,
        String name,
        String nameEng,
        BigDecimal totalShares,
        BigDecimal parValue,
        TradingStatus tradingStatus,
        MarketType marketType,
        AssetType assetType,
        BigDecimal leverage,
        Boolean isPtp
) {
    public static StockInfoResponse createFallback() {
        return StockInfoResponse.builder()
                .code("")
                .name("")
                .nameEng("")
                .totalShares(BigDecimal.ZERO)
                .parValue(BigDecimal.ZERO)
                .tradingStatus(TradingStatus.UNKNOWN)
                .marketType(MarketType.UNKNOWN)
                .assetType(AssetType.UNKNOWN)
                .leverage(BigDecimal.ZERO)
                .isPtp(false)
                .build();
    }

    public static Boolean setIsPtp(String isPtp) {
        return isPtp.equals("Y");
    }
}