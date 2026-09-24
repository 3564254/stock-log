package com.stocklog.modules.market.service;

import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.exchange.entity.ExchangeRate;
import com.stocklog.modules.market.exchange.provider.ExchangeRateProvider;
import com.stocklog.modules.market.exchange.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
class ExchangeService {
    private final ExchangeRateProvider exchangeRateProvider;
    private final ExchangeRateRepository exchangeRateRepository;

    @Transactional
    ExchangeRateInfo fetchExchangeRate() {
        ExchangeRate exchangeRate = exchangeRateRepository.findMainRate()
                .orElse(null);
        if (exchangeRate == null) {
            ExchangeRateInfo rateInfo = exchangeRateProvider.fetchExchangeRate();
            if (rateInfo == null) return ExchangeRateInfo.createFallback();
            exchangeRateRepository.save(ExchangeRate.of(rateInfo.baseRate()));
            return rateInfo;
        }
        log.info("[Exchange]: {}", exchangeRate.getRate());
        return ExchangeRateInfo.from(exchangeRate);
    }
}
