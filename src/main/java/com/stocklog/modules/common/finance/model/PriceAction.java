package com.stocklog.modules.common.finance.model;

import lombok.Builder;

@Builder
public record PriceAction(
        boolean isHH,
        boolean isHL,
        boolean isLH,
        boolean isLL,
        boolean isMacroUptrend,
        boolean isUptrend,
        boolean isSideways
) {
    public static PriceAction createFallback() {
        return new PriceAction(false, false, false, false, false, false, false);
    }
}