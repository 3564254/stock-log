package com.stocklog.infra.kis.info.api;

import com.stocklog.domain.stock.dto.request.StockRequest;
import com.stocklog.domain.stock.dto.vo.info.StockInfoDetail; // StockInfoDetail 반환 명시
import com.stocklog.domain.stock.provider.StockInfoProvider;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.infra.kis.common.KisResponseV1;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.info.dto.KisDomesticInfoOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import static com.stocklog.global.util.FinancialUtils.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisDomesticInfoProvider implements StockInfoProvider {
    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;



    @Override
    public StockInfoDetail fetchInfo(StockRequest request) {
        log.info("KIS 국내 fetchInfo: [{}]", request.code());

        try {
            KisDomesticInfoOutput output = callApi(request.code());
            if (output == null) return StockInfoDetail.createDetailFallback(); // 상세 전용 폴백 호출

            // SuperBuilder를 통해 부모(StockInfo)와 자식(StockInfoDetail) 필드 모두 채움
            return StockInfoDetail.builder()
                    .code(output.getCode()) // DTO의 정제된 코드 메서드 호출
                    .name(output.getName())
                    .sector(output.getSector())
                    .totalShares(safeBigDecimal(output.getTotalShares()))
                    .indexLarge(output.getIndexLarge())
                    .indexMedium(output.getIndexMedium())
                    .indexSmall(output.getIndexSmall())
                    .marketType(MarketType.from(output.getMarketCode())) // String 코드를 Enum으로 변환 매핑
                    .isTradeStop(output.getIsTradeStop())
                    .isAdminItem(output.getIsAdminItem())
                    .parValue(safeBigDecimal(output.getParValue()))
                    .build();
        } catch (Exception e) {
            log.error("국내 종목 정보 조회 중 오류: {}", e.getMessage());
            return StockInfoDetail.createDetailFallback();
        }
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

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isDomestic();
    }

}