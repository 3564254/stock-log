package com.stocklog.infra.kis.info.api;

import com.stocklog.domain.stock.common.StockRequest;
import com.stocklog.domain.stock.info.dto.response.StockInfoResponse;
import com.stocklog.domain.stock.info.StockInfoProvider;
import com.stocklog.domain.stock.common.type.AssetType;
import com.stocklog.domain.stock.common.type.MarketType;
import com.stocklog.domain.stock.common.type.TradingStatus;
import com.stocklog.infra.kis.common.KisResponseV1;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.info.dto.KisDomesticInfoOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import static com.stocklog.global.util.FinancialUtils.safeParse;


@Component
@Slf4j
@RequiredArgsConstructor
public class KisDomesticInfoProvider implements StockInfoProvider {

    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;

    public StockInfoResponse fetchInfo(StockRequest request) {
        log.info("KIS 국내 fetchInfo: [{}]", request.code());

        try {
            KisDomesticInfoOutput output = callApi(request.code());
            if (output == null) return StockInfoResponse.createFallback(); // 상세 전용 폴백 호출


            log.info("국내 getIsEtf: {}", output.getIsEtf());
            return StockInfoResponse.builder()
                    .code(output.getCode())
                    .name(output.getName())
                    .nameEng(output.getNameEng())
                    .totalShares(safeParse(output.getTotalShares()))
                    .parValue(safeParse(output.getParValue()))
                    .tradingStatus(TradingStatus.fromCode(output.getIsTradeStop()))
                    .assetType(AssetType.fromCode(output.getIsEtf()))
                    .leverage(safeParse(output.getLeverage()))
                    .isPtp(false)
                    .build();

        } catch (Exception e) {
            log.error("국내 종목 정보 조회 중 오류: {}", e.getMessage());
            return StockInfoResponse.createFallback();
        }
    }

    public boolean supports(MarketType marketType) {
        return marketType.isDomestic();
    }


    private KisDomesticInfoOutput callApi(String code) {
        KisResponseV1<KisDomesticInfoOutput> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/quotations/search-stock-info")
                        .queryParam("PDNO", code)
                        .queryParam("PRDT_TYPE_CD", "300")
                        .build())
                .headers(h -> {
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", "CTPF1002R");
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<KisResponseV1<KisDomesticInfoOutput>>() {})
                .block();

        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput() : null;
    }
}
