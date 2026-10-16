package com.stocklog.modules.market.analysis.dto;

import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisDto;
import lombok.Builder;

@Builder
public record MarketRegimeResponse(
        MarketRegimeDto marketRegimeDto,
        MarketDiagnosisDto diagnosisDto,
        java.time.LocalDate dataDate,
        com.stocklog.modules.market.analysis.service.horizon.HorizonService.HorizonReport horizons   // 단기·중기·장기 판정
) {
}
