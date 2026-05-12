package com.stocklog.domain.stock.common.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TradingStatus {
    NORMAL("01", "정상"),
    TRADING_PROHIBITED("02", "매매 불가"),
    BUY_PROHIBITED("03", "매수 불가"),
    SELL_PROHIBITED("04", "매도 불가"),
    UNKNOWN("", "상태 미상");

    private final String code;
    private final String description;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TradingStatus fromCode(String code) {
        for (TradingStatus status : TradingStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}