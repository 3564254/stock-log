package com.stocklog.modules.purchase.portfolio.dto;


import java.math.BigDecimal;

/**
 * 섹터별 자산 비중 정보를 담는 DTO
 * @param sectorName 섹터 이름 (예: 기술/반도체)
 * @param weight 비중 (%)
 */
public record SectorWeightDto(
        String sectorName,
        BigDecimal weight
) {
}