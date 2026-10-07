package com.stocklog.modules.stock.analysis.diagnosis.legacy.signal.vo;

import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.common.finance.context.MarketContext;
import lombok.Builder;
import java.util.List;

@Builder
public record EntrySignal(
        List<String> buySignals,
        List<String> sellSignals,
        int buyStrength,
        int sellStrength,
        ChartPhase phase,
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