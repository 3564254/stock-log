package com.stocklog.modules.purchase.portfolio.dto;


import java.math.BigDecimal;

/** 섹터별 자산 비중 정보를 담는 DTO */
public record SectorWeightDto(
        String sectorName,
        BigDecimal weight
) {
}