package com.stocklog.modules.stock.analysis.domain.signal.rule;

import com.stocklog.modules.stock.analysis.domain.signal.SignalResultBuilder;
import com.stocklog.modules.stock.analysis.domain.signal.vo.MarketContext;

public interface SignalRule {
    void evaluate(MarketContext ctx, SignalResultBuilder builder);
}