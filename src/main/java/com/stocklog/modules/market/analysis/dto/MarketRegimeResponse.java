package com.stocklog.modules.market.analysis.dto;

import com.stocklog.modules.market.analysis.diagnosis.MarketDiagnosisDto;
import lombok.Builder;

@Builder
public record MarketRegimeResponse(
        MarketRegimeDto marketRegimeDto,
        MarketDiagnosisDto diagnosisDto
) {
}
