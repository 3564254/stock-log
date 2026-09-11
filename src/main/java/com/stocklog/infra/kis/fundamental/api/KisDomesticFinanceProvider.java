package com.stocklog.infra.kis.fundamental.api;

import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.modules.stock.financial.StockFinanceProvider;
import com.stocklog.modules.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.modules.stock.financial.dto.response.StockEarningResponse;
import com.stocklog.modules.stock.financial.dto.response.StockFinancialsResponse;
import com.stocklog.infra.kis.common.KisResponseV1;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.fundamental.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static com.stocklog.global.util.FinancialUtils.isAvailable;
import static com.stocklog.global.util.FinancialUtils.safeParse;

@Component
@RequiredArgsConstructor
@Slf4j
public class KisDomesticFinanceProvider implements StockFinanceProvider {

    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;

    // API Path와 TR_ID를 배열 형태로 관리
    private final static String[] BALANCE_SHEET = {"/uapi/domestic-stock/v1/finance/balance-sheet", "FHKST66430100"};
    private final static String[] INCOME_STATEMENT = {"/uapi/domestic-stock/v1/finance/income-statement", "FHKST66430200"};
    private final static String[] FINANCIAL_RATIO = {"/uapi/domestic-stock/v1/finance/financial-ratio", "FHKST66430300"};
    private final static String[] PROFIT_RATIO = {"/uapi/domestic-stock/v1/finance/profit-ratio", "FHKST66430400"};
    private final static String[] OTHER_MAJOR_RATIOS = {"/uapi/domestic-stock/v1/finance/other-major-ratios", "FHKST66430500"};
    private final static String[] STABILITY_RATIO = {"/uapi/domestic-stock/v1/finance/stability-ratio", "FHKST66430600"};
    private final static String[] GROWTH_RATIO = {"/uapi/domestic-stock/v1/finance/growth-ratio", "FHKST66430800"};


    @Override
    public StockFinancialsResponse fetchFinancial(String symbol) {

        try {
            // 1. API 호출 로그는 callApi 내부에서 수행되므로 결과 확인에 집중
            List<KisIncomeStatementOutput> incomeList = callApiV1(INCOME_STATEMENT, symbol, new ParameterizedTypeReference<KisResponseV1<List<KisIncomeStatementOutput>>>() {});
            List<KisProfitRatioOutput> profitList = callApiV1(PROFIT_RATIO, symbol, new ParameterizedTypeReference<KisResponseV1<List<KisProfitRatioOutput>>>() {});
            List<KisFinancialRatioOutput> financialList = callApiV1(FINANCIAL_RATIO, symbol, new ParameterizedTypeReference<KisResponseV1<List<KisFinancialRatioOutput>>>() {});
            List<KisOtherMajorRatioOutput> otherMajorList = callApiV2(OTHER_MAJOR_RATIOS, symbol, new ParameterizedTypeReference<KisResponseV1<List<KisOtherMajorRatioOutput>>>() {});
            List<KisStabilityRatioOutput> stabilityList = callApiV2(STABILITY_RATIO, symbol, new ParameterizedTypeReference<KisResponseV1<List<KisStabilityRatioOutput>>>() {});

            // 데이터 누락 여부 모니터링 로그
            if (incomeList == null || profitList == null) {
                log.warn("[KIS-Finance] 핵심 재무 데이터(손익/수익성) 일부 응답 없음 - 종목코드: {}", symbol);
            }

            KisIncomeStatementOutput income = (incomeList != null && !incomeList.isEmpty()) ? incomeList.get(0) : null;
            KisProfitRatioOutput profit = (profitList != null && !profitList.isEmpty()) ? profitList.get(0) : null;
            KisFinancialRatioOutput financial = (financialList != null && !financialList.isEmpty()) ? financialList.get(0) : null;
            KisOtherMajorRatioOutput otherMajor = (otherMajorList != null && !otherMajorList.isEmpty()) ? otherMajorList.get(0) : null;
            KisStabilityRatioOutput stability = (stabilityList != null && !stabilityList.isEmpty()) ? stabilityList.get(0) : null;

            // 3. 계산 과정 로그
            BigDecimal operatingIncome = (income != null) ? safeParse(income.getOperatingIncome()) : BigDecimal.ZERO;
            BigDecimal revenue = (income != null) ? safeParse(income.getRevenue()): BigDecimal.ZERO;

            BigDecimal operatingMargin = BigDecimal.ZERO;
            if (isAvailable(operatingIncome) && isAvailable(revenue) && revenue.compareTo(BigDecimal.ZERO) != 0) {
                operatingMargin = (operatingIncome.divide(revenue, 4, RoundingMode.HALF_UP))
                        .multiply(new BigDecimal(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }


            StockFinancialDto financialDto = StockFinancialDto.builder()
                    .baseDate(LocalDate.now())
                    .grossMargin((profit != null) ? safeParse(profit.getGrossProfitMargin()) : BigDecimal.ZERO)
                    .operatingMargin(operatingMargin)
                    .roe((profit != null) ? safeParse(profit.getReturnOnEquity()) : BigDecimal.ZERO)
                    .roa((profit != null) ? safeParse(profit.getReturnOnTotalCapital()) : BigDecimal.ZERO)
                    .revenueGrowth((financial != null) ? safeParse(financial.getRevenueGrowthRate()) : BigDecimal.ZERO)
                    .epsGrowth((financial != null) ? safeParse(financial.getNetIncomeGrowthRate()) : BigDecimal.ZERO)
                    .dividendYield(BigDecimal.ZERO)
                    .payoutRatio((otherMajor != null) ? safeParse(otherMajor.getPayoutRatio()) : BigDecimal.ZERO)
                    .currentRatio((stability != null) ? safeParse(stability.getCurrentRatio()) : BigDecimal.ZERO)
                    .debtToEquity((financial != null) ? safeParse(financial.getDebtRatio()) : BigDecimal.ZERO)
                    .inventoryTurnover(BigDecimal.ZERO)
                    .build();

            return StockFinancialsResponse.builder()
                    .symbol(symbol)
                    .stockFinancialDto(financialDto)
                    .build();

        } catch (Exception e) {
            log.error("[KIS-Finance] 재무 정보 처리 중 치명적 오류 발생 - 종목코드: {}, 에러: {}", symbol, e.getMessage(), e);
            return StockFinancialsResponse.createFallback();
        }
    }

    @Override
    public StockEarningResponse fetchEarnings(String symbol) {
        return null;
    }


    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isDomestic();
    }

    // 제네릭 T의 타입을 런타임에 유지하기 위해 ParameterizedTypeReference 인자 추가
    private <T> T callApiV1(String[] apiInfo, String code, ParameterizedTypeReference<KisResponseV1<T>> typeRef) {
        String path = apiInfo[0];
        String trId = apiInfo[1];

        KisResponseV1<T> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path(path)
                        .queryParam("FID_DIV_CLS_CODE", "1")
                        .queryParam("fid_cond_mrkt_div_code","J")
                        .queryParam("fid_input_iscd",code)
                        .build()
                )
                .headers(h -> {
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", trId);
                })
                .retrieve()
                .bodyToMono(typeRef) // 명시된 타입 정보 전달
                .block();
        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput() : null;
    }

    // 제네릭 T의 타입을 런타임에 유지하기 위해 ParameterizedTypeReference 인자 추가
    private <T> T callApiV2(String[] apiInfo, String code, ParameterizedTypeReference<KisResponseV1<T>> typeRef) {
        String path = apiInfo[0];
        String trId = apiInfo[1];

        KisResponseV1<T> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path(path)
                        .queryParam("fid_div_cls_code", "1")
                        .queryParam("fid_cond_mrkt_div_code","J")
                        .queryParam("fid_input_iscd",code)
                        .build()
                )
                .headers(h -> {
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", trId);
                })
                .retrieve()
                .bodyToMono(typeRef) // 명시된 타입 정보 전달
                .block();
        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput() : null;
    }
}