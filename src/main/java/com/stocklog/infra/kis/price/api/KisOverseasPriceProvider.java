package com.stocklog.infra.kis.price.api;

import com.stocklog.domain.stock.common.StockRequest;
import com.stocklog.domain.stock.price.dto.response.StockPriceResponse;
import com.stocklog.domain.stock.price.StockPriceProvider;
import com.stocklog.domain.stock.common.type.MarketType;
import com.stocklog.infra.kis.common.KisResponseV1;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.price.dto.KisOverseasPriceOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

import static com.stocklog.global.util.FinancialUtils.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisOverseasPriceProvider implements StockPriceProvider {
    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;

    @Override
    public StockPriceResponse fetchPrice(StockRequest request) {
        KisOverseasPriceOutput output =callApi(request);
        if (output == null) return StockPriceResponse.createFallback();

        BigDecimal currentPrice = safeParse(output.getCurrentPrice());
        BigDecimal basePrice = safeParse(output.getBasePrice());

        return StockPriceResponse.builder()
                .currentPrice(currentPrice)
                .changePrice(calculateChangePrice(currentPrice, basePrice))
                .changeRate(calculateChangeRate(currentPrice, basePrice))
                .basePrice(basePrice)
                .per(safeParse(output.getPer()))
                .pbr(safeParse(output.getPbr()))
                .eps(safeParse(output.getEps()))
                .bps(safeParse(output.getBps()))
                .marketCap(safeParse(output.getMarketCap()))
                .w52High(safeParse(output.getW52High()))
                .w52Low(safeParse(output.getW52Low()))
                .volume(safeParse(output.getVolume()))
                .build();

    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isOverseas();
    }


    private KisOverseasPriceOutput callApi(StockRequest request) {
        KisResponseV1<KisOverseasPriceOutput> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/price-detail")
                        .queryParam("authorization", "Bearer " + tokenManager.getAccessToken())
                        .queryParam("EXCD", request.marketType().getExchangeCode())
                        .queryParam("SYMB", request.code())
                        .build())
                .headers(h -> {
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", "HHDFS76200200");
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<KisResponseV1<KisOverseasPriceOutput>>() {})
                .block();
        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput() : null;
    }
}
