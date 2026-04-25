package com.stocklog.domain.marketdata.exchange.provider;

import com.stocklog.domain.marketdata.exchange.dto.ExchangeRateInfo;

public interface ExchangeRateProvider {
    ExchangeRateInfo fetchExchangeRate();
}
