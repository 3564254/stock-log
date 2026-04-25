package com.stocklog.infra.kis.price.api;

import com.stocklog.domain.stock.dto.request.StockRequest;
import com.stocklog.domain.stock.dto.vo.price.StockPriceDetail;
import com.stocklog.domain.stock.provider.StockPriceProvider;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.infra.kis.common.KisResponseV1;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.price.dto.KisDomesticPriceOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import static com.stocklog.global.util.FinancialUtils.safeBigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisDomesticPriceProvider implements StockPriceProvider {
    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;

    @Override
    public StockPriceDetail fetchPrice(StockRequest request) {
        KisDomesticPriceOutput output = callApi(request.code());
        if (output == null) return StockPriceDetail.createFallback(); // 상세 전용 폴백 호출

        return StockPriceDetail.builder()
                .currentPrice(safeBigDecimal(output.getCurrentPrice()))
                .changePrice(safeBigDecimal(output.getChangePrice()))
                .changeRate(safeBigDecimal(output.getChangeRate()))
                .basePrice(safeBigDecimal(output.getBasePrice()))
                .per(safeBigDecimal(output.getPer()))
                .pbr(safeBigDecimal(output.getPbr()))
                .eps(safeBigDecimal(output.getEps()))
                .bps(safeBigDecimal(output.getBps()))
                .marketCap(safeBigDecimal(output.getMarketCap()))
                .volume(safeBigDecimal(output.getVolume()))
                .w52High(safeBigDecimal(output.getW52High()))
                .w52Low(safeBigDecimal(output.getW52Low()))
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