package com.stocklog.modules.stock.info.dto;

import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.domain.type.*;
import com.stocklog.modules.stock.info.StockInfo;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.Builder;

import java.math.BigDecimal;


@Builder
public record StockInfoDto(
        String code,
        String name,
        String nameEng,
        BigDecimal totalShares,
        BigDecimal parValue,
        TradingStatus tradingStatus,
        String sector,
        Nation nation,
        Currency currency,
        MarketType marketType,
        AssetType assetType,
        BigDecimal leverage,
        boolean isPtp
) {
    public static StockInfoDto from(StockInfo entity) {
        return StockInfoDto.builder()
                .code(entity.getCode())
                .name(entity.getName())
                .nameEng(entity.getNameEng())
                .totalShares(entity.getTotalShares())
                .parValue(entity.getParValue())
                .tradingStatus(TradingStatus.NORMAL)
                .sector(entity.getSector())
                .nation(entity.getNation())
                .currency(entity.getCurrency())
                .marketType(entity.getMarketType())
                .assetType(entity.getAssetType())
                .leverage(entity.getLeverage())
                .isPtp(entity.isPtp())
                .build();
    }

    public static StockInfoDto of(StockDto stock, StockInfoResponse response) {
        return StockInfoDto.builder()
                .code(response.code())
                .name(response.name())
                .nameEng(response.nameEng())
                .totalShares(response.totalShares())
                .parValue(response.parValue())
                .tradingStatus(response.tradingStatus())
                .sector(stock.sector())
                .nation(stock.nation())
                .marketType(stock.market())
                .assetType(response.assetType())
                .leverage(response.leverage())
                .isPtp(response.isPtp())
                .build();
    }


    public static StockInfo toEntity(StockInfoDto infoDto) {
        return StockInfo.builder()
                .code(infoDto.code())
                .name(infoDto.name())
                .nameEng(infoDto.nameEng())
                .sector(infoDto.sector())
                .nation(infoDto.nation())
                .marketType(infoDto.marketType())
                .currency(infoDto.currency())
                .totalShares(infoDto.totalShares())
                .parValue(infoDto.parValue())
                .assetType(infoDto.assetType())
                .tradingStatus(infoDto.tradingStatus())
                .leverage(infoDto.leverage())
                .isPtp(infoDto.isPtp())
                .build();
    }
}
