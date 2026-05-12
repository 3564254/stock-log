package com.stocklog.domain.marketdata.universe.dto;

import com.stocklog.domain.marketdata.universe.entity.Universe;
import com.stocklog.domain.stock.analysis.domain.signal.vo.MarketPhase;
import com.stocklog.domain.marketdata.universe.entity.UniverseStatus;
import lombok.Builder;


import java.math.BigDecimal;

@Builder
public record UniverseDto(
        String code,
        String name,
        Universe universe,
        MarketPhase phase,
        BigDecimal currentPrice,
        BigDecimal changeRate,
        BigDecimal entryPrice
) {
    public static UniverseStatus toEntity(UniverseDto dashboardDto) {
        return UniverseStatus.builder()
                .code(dashboardDto.code())
                .phase(dashboardDto.phase())
                .currentPrice(dashboardDto.currentPrice())
                .changeRate(dashboardDto.changeRate())
                .entryPrice(dashboardDto.entryPrice())
                .build();
    }

    public static UniverseDto from(UniverseStatus universeStatus) {
        return UniverseDto.builder()
                .code(universeStatus.getCode())
                .name(Universe.getSectorName(universeStatus.getCode()))
                .universe(Universe.getUniverse(universeStatus.getCode()))
                .phase(universeStatus.getPhase())
                .currentPrice(universeStatus.getCurrentPrice())
                .changeRate(universeStatus.getChangeRate())
                .entryPrice(universeStatus.getEntryPrice())
                .build();
    }

}
