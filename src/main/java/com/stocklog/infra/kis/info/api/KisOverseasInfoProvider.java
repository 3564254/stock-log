package com.stocklog.infra.kis.info.api;

import com.stocklog.domain.stock.dto.request.StockRequest;
import com.stocklog.domain.stock.dto.vo.info.StockInfoDetail;
import com.stocklog.domain.stock.provider.StockInfoProvider;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.infra.kis.common.KisResponseV1;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.info.dto.KisOverseasInfoOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import static com.stocklog.global.util.FinancialUtils.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class KisOverseasInfoProvider implements StockInfoProvider {

    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;


    @Override
    public StockInfoDetail fetchInfo(StockRequest request) {
        log.info("KIS 해외 fetchInfo [{}]", request.code());

        try {
            KisOverseasInfoOutput output = callApi(request);
            if (output == null) return StockInfoDetail.createDetailFallback();

            return StockInfoDetail.builder()
                    .code(output.getCode())
                    .name(output.getName())
                    .sector("-")
                    .totalShares(safeBigDecimal(output.getTotalShares()))
                    .indexLarge("-")
                    .indexMedium("-")
                    .indexSmall("-")
                    .marketType(MarketType.from(output.getMarketCode()))
                    .isTradeStop(output.getIsTradeStop())
                    .isAdminItem("-")
                    .parValue(safeBigDecimal(output.getParValue()))
                    .build();
        } catch (Exception e) {
            log.error("해외 종목 정보 조회 중 오류 {}", e.getMessage());
            return StockInfoDetail.createDetailFallback();
        }
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isOverseas();
    }


    private KisOverseasInfoOutput callApi(StockRequest request) {
        KisResponseV1<KisOverseasInfoOutput> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/search-info")
                        .queryParam("PRDT_TYPE_CD", request.marketType().getKisCode())
                        .queryParam("PDNO", request.code())
                        .build())
                .headers(h -> {
                    h.add("content-type", "application/json; charset=utf-8");
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", "CTPF1702R");
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<KisResponseV1<KisOverseasInfoOutput>>() {})
                .block();
        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput() : null;
    }
}
