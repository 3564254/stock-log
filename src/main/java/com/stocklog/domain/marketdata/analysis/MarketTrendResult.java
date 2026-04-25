package com.stocklog.domain.marketdata.analysis;

import java.util.List;

public record MarketTrendResult(
        int riskScore,
        String regime,
        List<String> signals
) {}
