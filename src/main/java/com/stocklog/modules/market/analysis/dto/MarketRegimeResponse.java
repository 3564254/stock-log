package com.stocklog.modules.market.analysis.dto;

import com.stocklog.modules.market.diagnosis.MarketDiagnosisDto;
import lombok.Builder;

@Builder
public record MarketRegimeResponse(
        MarketRegimeDto marketRegimeDto,
        MarketDiagnosisDto diagnosisDto
) {
}
