package com.stocklog.modules.stock.screening;

import java.math.BigDecimal;

/** 시가총액 순위용 종목 한 줄. marketCap 은 원화 기준, 없으면 null */
public record MarketCapStock(
        String code,
        String name,
        BigDecimal marketCap
) {
}
