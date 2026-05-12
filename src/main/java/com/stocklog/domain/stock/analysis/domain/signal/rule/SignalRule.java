package com.stocklog.domain.stock.analysis.domain.signal.rule;

import com.stocklog.domain.stock.analysis.domain.signal.SignalResultBuilder;
import com.stocklog.domain.stock.analysis.domain.signal.vo.MarketContext;

public interface SignalRule {
    void evaluate(MarketContext ctx, SignalResultBuilder builder);
}