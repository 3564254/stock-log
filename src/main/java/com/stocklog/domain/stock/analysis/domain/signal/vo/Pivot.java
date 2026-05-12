package com.stocklog.domain.stock.analysis.domain.signal.vo;

import com.stocklog.domain.stock.common.type.Period;
import lombok.Builder;

@Builder
public record Pivot(
        double p,   // 피벗 포인트 (중심선)
        double r1,  // 1차 저항선 (Resistance 1)
        double r2,  // 2차 저항선 (Resistance 2)
        double s1,  // 1차 지지선 (Support 1)
        double s2,   // 2차 지지선 (Support 2)
        Period period
) {
    public static Pivot of(double high, double low, double close, Period period) {
        double p = (high + low + close) / 3.0;
        return new Pivot(
                p,
                (2 * p) - low,      // r1
                p + (high - low),   // r2
                (2 * p) - high,     // s1
                p - (high - low),  // s2
                period
        );
    }

    public static Pivot of(double high, double low, double close) {
        double p = (high + low + close) / 3.0;
        return new Pivot(
                p,
                (2 * p) - low,      // r1
                p + (high - low),   // r2
                (2 * p) - high,     // s1
                p - (high - low),  // s2
                Period.DAY
        );
    }
}
