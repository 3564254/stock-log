package com.stocklog.modules.stock.analysis.domain.signal.vo;

import lombok.Builder;
import java.util.List;

@Builder
public record EntrySignal(
        List<String> buySignals,
        List<String> sellSignals,
        int buyStrength,
        int sellStrength,
        MarketPhase phase,
        MarketContext marketContext,
        double primaryEntry,
        double secondaryEntry,
        double stopLoss,
        double primaryTarget,
        double secondaryTarget,
        double trailingStop,
        double riskPerShare,
        double tradeRiskPercent,
        double recommendedWeight,
        String verdict
) {}