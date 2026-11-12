package com.stocklog.modules.market.analysis.dto;

import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisDto;
import lombok.Builder;

@Builder
public record MarketRegimeResponse(
        MarketRegimeDto marketRegimeDto,
        MarketDiagnosisDto diagnosisDto,
        java.time.LocalDate dataDate,
        com.stocklog.modules.market.analysis.service.horizon.HorizonService.HorizonReport horizons,  // 단기·중기·장기 판정
        com.stocklog.modules.market.analysis.service.allocation.CashAllocationService.CashGuide cashGuide  // 권장 현금 비중 (실패하면 null)
) {
}
