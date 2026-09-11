package com.stocklog.infra.exchange.api;

import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.exchange.provider.ExchangeRateProvider;
import com.stocklog.infra.exchange.config.ExchangeProperties;
import com.stocklog.infra.exchange.dto.ExchangeRateOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateApiProvider implements ExchangeRateProvider {

    private final ExchangeProperties exchangeProperties;

    @Override
    public ExchangeRateInfo fetchExchangeRate() {
        String apiKey = exchangeProperties.authKey();
        String url = "https://v6.exchangerate-api.com/v6/" + apiKey + "/latest/USD";

        RestTemplate restTemplate = new RestTemplate();
        ExchangeRateOutput response = restTemplate.getForObject(url, ExchangeRateOutput.class);

        if (response != null && "success".equals(response.getResult())) {
            BigDecimal usdRate = response.getConversionRates().get("KRW");

            return ExchangeRateInfo.builder()
                    .baseCurrency("KRW")
                    .baseRate(usdRate)
                    .build();
        }
        return ExchangeRateInfo.createFallback();
    }
}
