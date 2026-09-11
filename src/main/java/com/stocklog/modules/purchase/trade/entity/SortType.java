package com.stocklog.modules.purchase.trade.entity;

import org.springframework.data.domain.Sort;

public enum SortType {
    DATE_DEC,
    DATE_ASC,
    PROFIT;

    public static Sort getSort(SortType type) {
        return switch (type) {
            case DATE_ASC -> Sort.by("tradeDate").ascending();
            case PROFIT -> Sort.by("realizedProfit").descending();
            default -> Sort.by("tradeDate").descending();
        };
    }
    public static Sort getSort() {
        return Sort.by("tradeDate").descending();
    }

}