package com.stocklog.modules.common.finance.model;

import com.stocklog.modules.common.type.Period;
import lombok.Builder;

@Builder
public record PivotSet(
        Pivot dailyPivot,
        Pivot weeklyPivot
) {
    public static PivotSet createFallback() {
        return new PivotSet(
                Pivot.createFallback(Period.DAY),
                Pivot.createFallback(Period.WEEK)
        );
    }
}