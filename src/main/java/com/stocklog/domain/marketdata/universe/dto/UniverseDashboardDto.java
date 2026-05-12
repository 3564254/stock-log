package com.stocklog.domain.marketdata.universe.dto;

import lombok.Builder;

@Builder
public record UniverseDashboardDto(
        UniverseDto sp500,
        UniverseDto NAS,
        UniverseDto xlk,
        UniverseDto xlp,
        UniverseDto xli,
        UniverseDto xle
) {
}
