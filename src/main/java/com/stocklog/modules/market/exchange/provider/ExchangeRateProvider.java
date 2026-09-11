package com.stocklog.modules.market.exchange.provider;

import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;

public interface ExchangeRateProvider {
    ExchangeRateInfo fetchExchangeRate();
}
