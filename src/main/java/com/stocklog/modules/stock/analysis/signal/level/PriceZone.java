package com.stocklog.modules.stock.analysis.signal.level;

import java.util.List;

/** 가격 구간 */
public record PriceZone(double low, double high, double strength, List<String> sources) {
    public double mid() { return (low + high) / 2; }
}
