package com.stocklog.domain.marketdata.exchange.service;

import com.stocklog.domain.marketdata.exchange.dto.ExchangeRateInfo;
import com.stocklog.domain.marketdata.exchange.entity.ExchangeRate;
import com.stocklog.domain.marketdata.exchange.provider.ExchangeRateProvider;
import com.stocklog.domain.marketdata.exchange.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExchangeService {
    private final ExchangeRateProvider exchangeRateProvider;
    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateInfo fetchExchangeRate() {
        ExchangeRate exchangeRate = exchangeRateRepository.findMainRate()
                .orElse(null);
        if (exchangeRate == null) {
            ExchangeRateInfo rateInfo = exchangeRateProvider.fetchExchangeRate();
            log.info("오늘의 환율: {}", rateInfo);
            if (rateInfo == null) return ExchangeRateInfo.createFallback();
            exchangeRateRepository.save(ExchangeRate.of(rateInfo.baseRate()));
            return rateInfo;
        }
        return ExchangeRateInfo.from(exchangeRate);
    }
}
