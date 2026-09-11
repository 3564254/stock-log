package com.stocklog.infra.finnhub.fundamental.api;

import com.stocklog.modules.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.modules.stock.financial.dto.response.StockEarningResponse;
import com.stocklog.modules.stock.financial.dto.response.StockFinancialsResponse;
import com.stocklog.modules.stock.financial.StockFinanceProvider;
import com.stocklog.modules.stock.domain.type.MarketType;
import com.stocklog.infra.finnhub.config.FinProperties;
import com.stocklog.infra.finnhub.fundamental.dto.FinStockFinancialsOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;

@Component
@Slf4j
public class FinOverseasFinanceProvider implements StockFinanceProvider {

    private final WebClient webClient;
    private final FinProperties finProperties;

    public FinOverseasFinanceProvider(@Qualifier("finnhubWebClient") WebClient webClient, FinProperties finProperties) {
        this.webClient = webClient;
        this.finProperties = finProperties;
    }


    @Override
    public StockFinancialsResponse fetchFinancial(String symbol) {
        try {
            FinStockFinancialsOutput output = callFinancialsApi(symbol);
            if (output == null) return StockFinancialsResponse.createFallback();
            if (output.getMetric() == null) {
                log.warn("해당 종목의 Metric 정보가 없습니다: {}", symbol);
                return StockFinancialsResponse.createFallback();
            }
            return getFinancialsResponse(output);
        } catch (Exception e) {
            log.error("FIN 해외 Financials 정보 조회 중 오류 {}", e.getMessage());
        }
        return StockFinancialsResponse.createFallback();
    }

    @Override
    public StockEarningResponse fetchEarnings(String symbol) {
        return null;
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isOverseas();
    }

    private FinStockFinancialsOutput callFinancialsApi(String symbol){
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/stock/metric")
                        .queryParam("symbol", symbol)
                        .queryParam("metric","all")
                        .build()
                )
                .header("X-Finnhub-Token", finProperties.getApiKey())
                .retrieve()
                .bodyToMono(FinStockFinancialsOutput.class)
                .block();
    }


    private StockFinancialsResponse getFinancialsResponse(FinStockFinancialsOutput output) {
        // 1. Metric 레코드 빌더를 통한 데이터 매핑
        StockFinancialDto stockFinancialDto = StockFinancialDto.builder()
                .baseDate(LocalDate.now())
                // --- 수익성 ---
                .grossMargin(output.getMetric().getGrossMarginTTM())
                .operatingMargin(output.getMetric().getOperatingMarginTTM())
                .netMargin(output.getMetric().getNetProfitMarginTTM())
                .roe(output.getMetric().getRoeTTM())
                .roa(output.getMetric().getRoaTTM())

                // --- 성장성 ---
                .revenueGrowth(output.getMetric().getRevenueGrowthTTMYoy())
                .epsGrowth(output.getMetric().getEpsGrowthTTMYoy())

                // --- 배당 ---
                .dividendYield(output.getMetric().getDividendYieldTTM())
                .payoutRatio(output.getMetric().getPayoutRatioTTM())

                // --- 안정성 및 효율성 ---
                .currentRatio(output.getMetric().getCurrentRatioQuarterly())
                .debtToEquity(output.getMetric().getDebtToEquityQuarterly())
                .inventoryTurnover(output.getMetric().getInventoryTurnoverTTM())
                .build();

        // 2. 최종 Response 객체 생성 및 반환
        return StockFinancialsResponse.builder()
                .symbol(output.getSymbol())
                .stockFinancialDto(stockFinancialDto)
                .build();
    }
}
