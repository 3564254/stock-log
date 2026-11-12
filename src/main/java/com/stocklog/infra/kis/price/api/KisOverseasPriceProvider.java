package com.stocklog.infra.kis.price.api;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.price.dto.StockPriceResponse;
import com.stocklog.modules.stock.price.StockPriceProvider;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.infra.kis.common.KisResponseV1;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.price.dto.KisDayPriceOutput;
import com.stocklog.infra.kis.price.dto.KisOverseasPriceOutput;
import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.market.open.MarketOpenProvider;
import com.stocklog.modules.market.open.MarketOpenType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.stocklog.modules.common.finance.util.FinancialUtils.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class KisOverseasPriceProvider implements StockPriceProvider {
    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;
    private final MarketOpenProvider marketOpenProvider;

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Override
    public StockPriceResponse fetchPrice(StockRequest request) {
        KisOverseasPriceOutput output =callApi(request);
        if (output == null) return StockPriceResponse.createFallback();

        BigDecimal currentPrice = safeParse(output.getCurrentPrice());
        BigDecimal basePrice = safeParse(output.getBasePrice());
        BigDecimal volume = safeParse(output.getVolume());

        // 데이마켓(미국 주간거래) 시간에는 정규장 코드(NAS 등)로 물으면 전일 종가에서 멈춰 있다
        if (isDayMarket()) {
            KisDayPriceOutput day = callDayApi(request);
            BigDecimal dayPrice = day != null ? safeParse(day.getCurrentPrice()) : null;
            if (dayPrice != null && dayPrice.signum() > 0) {
                currentPrice = dayPrice;
                BigDecimal dayBase = safeParse(day.getBasePrice());
                if (dayBase != null && dayBase.signum() > 0) basePrice = dayBase;
                BigDecimal dayVol = safeParse(day.getVolume());
                if (dayVol != null) volume = dayVol;
            }
        }

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
                .volume(volume)
                .build();

    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isOverseas();
    }


    /** 미국 데이마켓 시간인지 (장 시간표 조회 실패 시 뉴욕 시간으로 판단) */
    private boolean isDayMarket() {
        try {
            var info = marketOpenProvider.fetchMarketOpenInfo(Nation.US);
            if (info != null) return info.isMarketOpen() && info.marketOpenType() == MarketOpenType.DAY_MARKET;
        } catch (Exception e) {
            log.debug("[KisOverseasPrice] 장 시간표 조회 실패 → 시계로 판단: {}", e.getMessage());
        }
        ZonedDateTime ny = ZonedDateTime.now(NEW_YORK);
        DayOfWeek d = ny.getDayOfWeek();
        LocalTime t = ny.toLocalTime();
        boolean evening = !t.isBefore(LocalTime.of(20, 0)) && d != DayOfWeek.FRIDAY && d != DayOfWeek.SATURDAY;
        boolean night = t.isBefore(LocalTime.of(4, 0)) && d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
        return evening || night;
    }

    /** 정규장 거래소 코드 → 주간거래 시세 코드 */
    private static String dayCode(String exchangeCode) {
        return switch (exchangeCode) {
            case "NAS" -> "BAQ";
            case "NYS" -> "BAY";
            case "AMS" -> "BAA";
            default -> null;
        };
    }

    private KisDayPriceOutput callDayApi(StockRequest request) {
        String code = dayCode(request.marketType().getExchangeCode());
        if (code == null) return null;
        try {
            KisResponseV1<KisDayPriceOutput> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/uapi/overseas-price/v1/quotations/price")
                            .queryParam("AUTH", "")
                            .queryParam("EXCD", code)
                            .queryParam("SYMB", request.code())
                            .build())
                    .headers(h -> {
                        h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                        h.add("appkey", properties.appKey());
                        h.add("appsecret", properties.appSecret());
                        h.add("tr_id", "HHDFS00000300");
                    })
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<KisResponseV1<KisDayPriceOutput>>() {})
                    .block();
            return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput() : null;
        } catch (Exception e) {
            log.info("[KisOverseasPrice] {} 주간거래 시세 실패 → 정규장 값 사용: {}", request.code(), e.getMessage());
            return null;
        }
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
