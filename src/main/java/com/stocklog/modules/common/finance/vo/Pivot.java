package com.stocklog.modules.common.finance.vo;

import com.stocklog.modules.stock.domain.type.Period;
import lombok.Builder;

@Builder
public record Pivot(
        double p,   // 피벗 포인트 (중심선)
        double r1,  // 1차 저항선
        double r2,  // 2차 저항선
        double r3,  // 3차 저항선 (극단적 과열)
        double s1,  // 1차 지지선
        double s2,  // 2차 지지선
        double s3,  // 3차 지지선 (극단적 투매)
        Period period
) {
    public static Pivot of(double high, double low, double close, Period period) {
        double p = (high + low + close) / 3.0;
        double range = high - low;

        return new Pivot(
                p,
                (2 * p) - low,            // R1
                p + range,                // R2
                high + 2 * (p - low),     // R3 (고가 + 2 * (중심 - 저가))
                (2 * p) - high,           // S1
                p - range,                // S2
                low - 2 * (high - p),     // S3 (저가 - 2 * (고가 - 중심))
                period
        );
    }

    public static Pivot of(double high, double low, double close) {
        double p = (high + low + close) / 3.0;
        double range = high - low;

        return new Pivot(
                p,
                (2 * p) - low,            // R1
                p + range,                // R2
                high + 2 * (p - low),     // R3 (고가 + 2 * (중심 - 저가))
                (2 * p) - high,           // S1
                p - range,                // S2
                low - 2 * (high - p),     // S3 (저가 - 2 * (고가 - 중심))
                Period.DAY
        );
    }

    public static Pivot createFallback(Period period) {
        return new Pivot(0, 0, 0, 0, 0, 0, 0, period);
    }
}
