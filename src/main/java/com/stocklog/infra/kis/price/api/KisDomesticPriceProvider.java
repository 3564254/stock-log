package com.stocklog.infra.kis.price.api;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.price.dto.StockPriceResponse;
import com.stocklog.modules.stock.price.StockPriceProvider;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.infra.kis.common.KisResponseV1;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.price.dto.KisDomesticPriceOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import static com.stocklog.modules.common.finance.util.FinancialUtils.safeParse;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisDomesticPriceProvider implements StockPriceProvider {
    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;

    @Override
    public StockPriceResponse fetchPrice(StockRequest request) {
        KisDomesticPriceOutput output = callApi(request.code());
        if (output == null) return StockPriceResponse.createFallback(); // 상세 전용 폴백 호출

        return StockPriceResponse.builder()
                .currentPrice(safeParse(output.getCurrentPrice()))
                .changePrice(safeParse(output.getChangePrice()))
                .changeRate(safeParse(output.getChangeRate()))
                .basePrice(safeParse(output.getBasePrice()))
                .per(safeParse(output.getPer()))
                .pbr(safeParse(output.getPbr()))
                .eps(safeParse(output.getEps()))
                .bps(safeParse(output.getBps()))
                .marketCap(safeParse(output.getMarketCap()))
                .volume(safeParse(output.getVolume()))
                .w52High(safeParse(output.getW52High()))
                .w52Low(safeParse(output.getW52Low()))
                .build();
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isDomestic();
    }

    private KisDomesticPriceOutput callApi(String code) {
        KisResponseV1<KisDomesticPriceOutput> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", code)
                        .build())
                .headers(h -> {
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", "FHKST01010100");
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<KisResponseV1<KisDomesticPriceOutput>>() {})
                .block();
        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput() : null;
    }
}