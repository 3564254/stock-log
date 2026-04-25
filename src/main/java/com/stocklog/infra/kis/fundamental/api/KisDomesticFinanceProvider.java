package com.stocklog.infra.kis.fundamental.api;

import com.stocklog.domain.stock.dto.response.StockEarningResponse;
import com.stocklog.domain.stock.dto.response.StockFinancialsResponse;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.FinancialIndicators;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.FinancialPeriodData;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.FinancialStatements;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail.BalanceSheet;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail.CashFlow;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail.IncomeStatement;
import com.stocklog.domain.stock.provider.StockFinanceProvider;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.infra.kis.common.KisResponseV1;
import com.stocklog.infra.kis.config.KisProperties;
import com.stocklog.infra.kis.config.client.KisTokenManager;
import com.stocklog.infra.kis.fundamental.dto.KisBalanceSheetOutput;
import com.stocklog.infra.kis.fundamental.dto.KisIncomeStatementOutput;
import com.stocklog.infra.kis.fundamental.dto.KisIndicatorsOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class KisDomesticFinanceProvider implements StockFinanceProvider {

    private final WebClient webClient;
    private final KisProperties properties;
    private final KisTokenManager tokenManager;

    @Override
    public StockFinancialsResponse getFinancialStatements(String symbol) {
        log.info("KIS 국내 Financials: [{}]", symbol);

        try {
            List<KisBalanceSheetOutput> balanceSheetOutputs = callBalanceSheetsApi(symbol);
            List<KisIncomeStatementOutput> incomeStatementOutputs = callIncomeStatementApi(symbol);
            List<KisIndicatorsOutput> indicatorsOutputs = callIndicatorsOutput(symbol);

            if (balanceSheetOutputs == null || incomeStatementOutputs == null || indicatorsOutputs == null)
                return StockFinancialsResponse.createFallback();
            return getFinancialResponse(balanceSheetOutputs, incomeStatementOutputs, indicatorsOutputs);
        } catch (Exception e) {
            log.info("KIS 국내 Financials 정보 조회 중 오류 {}", e.getMessage());
        }
        return StockFinancialsResponse.createFallback();
    }

    @Override
    public StockEarningResponse getEarningsSurprises(String symbol) {
        return null;
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isDomestic();
    }
    private StockFinancialsResponse getFinancialResponse(List<KisBalanceSheetOutput> balanceSheetOutputs,
                                                         List<KisIncomeStatementOutput> incomeStatementOutputs,
                                                         List<KisIndicatorsOutput> indicatorsOutputs) {

        if (balanceSheetOutputs == null || balanceSheetOutputs.isEmpty() ||
                incomeStatementOutputs == null || incomeStatementOutputs.isEmpty()) {
            return StockFinancialsResponse.createFallback();
        }

        BigDecimal UNIT_HUNDRED_MILLION = new BigDecimal("100000000");

        int size = Math.min(balanceSheetOutputs.size(), incomeStatementOutputs.size());
        Map<String, IncomeStatement> ytdIsMap = new HashMap<>();

        // 1단계: YTD(누적) 데이터를 먼저 Map에 담아둡니다.
        for (int i = 0; i < size; i++) {
            KisBalanceSheetOutput bs = balanceSheetOutputs.get(i);
            KisIncomeStatementOutput is = incomeStatementOutputs.get(i);

            String yymm = bs.getClosingDate();
            if (yymm != null && yymm.length() == 6) {
                int year = Integer.parseInt(yymm.substring(0, 4));
                int quarter = (Integer.parseInt(yymm.substring(4, 6)) - 1) / 3 + 1;

                IncomeStatement isYtd = IncomeStatement.builder()
                        .revenue(toBigDecimal(is.getRevenue()).multiply(UNIT_HUNDRED_MILLION))
                        .grossProfit(toBigDecimal(is.getGrossProfit()).multiply(UNIT_HUNDRED_MILLION))
                        .operatingExpenses(toBigDecimal(is.getOperatingExpenses()).multiply(UNIT_HUNDRED_MILLION))
                        .operatingIncome(toBigDecimal(is.getOperatingIncome()).multiply(UNIT_HUNDRED_MILLION))
                        .netIncomeLoss(toBigDecimal(is.getNetIncomeLoss()).multiply(UNIT_HUNDRED_MILLION))
                        .epsBasic(toBigDecimal(is.getEps()))
                        .epsDiluted(toBigDecimal(is.getEps()))
                        .build();

                ytdIsMap.put(year + "-" + quarter, isYtd);
            }
        }

        List<FinancialPeriodData> periodDataList = new ArrayList<>();

        // 2단계: 누적 데이터를 분기 데이터로 변환하며 리스트를 생성합니다.
        for (int i = 0; i < size; i++) {
            KisBalanceSheetOutput bsOut = balanceSheetOutputs.get(i);

            int year = 0;
            int quarter = 0;
            String yymm = bsOut.getClosingDate();
            if (yymm != null && yymm.length() == 6) {
                year = Integer.parseInt(yymm.substring(0, 4));
                quarter = (Integer.parseInt(yymm.substring(4, 6)) - 1) / 3 + 1;
            }

            BalanceSheet balanceSheet = BalanceSheet.builder()
                    .assets(toBigDecimal(bsOut.getAssets()).multiply(UNIT_HUNDRED_MILLION))
                    .liabilities(toBigDecimal(bsOut.getLiabilities()).multiply(UNIT_HUNDRED_MILLION))
                    .stockholdersEquity(toBigDecimal(bsOut.getStockholdersEquity()).multiply(UNIT_HUNDRED_MILLION))
                    .currentAssets(toBigDecimal(bsOut.getCurrentAssets()).multiply(UNIT_HUNDRED_MILLION))
                    .currentLiabilities(toBigDecimal(bsOut.getCurrentLiabilities()).multiply(UNIT_HUNDRED_MILLION))
                    .build();

            IncomeStatement currentYtd = ytdIsMap.get(year + "-" + quarter);
            IncomeStatement finalIs = currentYtd;
            boolean isCalculated = true; // ✨ 계산 성공 플래그

            // 1분기가 아니라면 이전 분기 데이터를 빼야 합니다.
            if (quarter > 1 && currentYtd != null) {
                IncomeStatement prevYtd = ytdIsMap.get(year + "-" + (quarter - 1));

                if (prevYtd != null) {
                    finalIs = IncomeStatement.builder()
                            .revenue(currentYtd.getRevenue().subtract(prevYtd.getRevenue()))
                            .grossProfit(currentYtd.getGrossProfit().subtract(prevYtd.getGrossProfit()))
                            .operatingExpenses(currentYtd.getOperatingExpenses().subtract(prevYtd.getOperatingExpenses()))
                            .operatingIncome(currentYtd.getOperatingIncome().subtract(prevYtd.getOperatingIncome()))
                            .netIncomeLoss(currentYtd.getNetIncomeLoss().subtract(prevYtd.getNetIncomeLoss()))
                            .epsBasic(currentYtd.getEpsBasic())
                            .epsDiluted(currentYtd.getEpsDiluted())
                            .build();
                } else {
                    // ✨ 전분기 데이터가 없어 뺄셈을 못했다면 리스트에 넣지 않습니다.
                    isCalculated = false;
                }
            }

            // 정상적으로 계산(또는 1분기 데이터)된 경우에만 리스트 추가
            if (isCalculated && finalIs != null) {
                CashFlow cashFlow = CashFlow.builder()
                        .netIncomeLoss(finalIs.getNetIncomeLoss())
                        .operatingCashFlow(BigDecimal.ZERO)
                        .capex(BigDecimal.ZERO)
                        .build();

                FinancialStatements statements = FinancialStatements.builder()
                        .balanceSheet(balanceSheet)
                        .incomeStatement(finalIs)
                        .cashFlow(cashFlow)
                        .build();

                periodDataList.add(FinancialPeriodData.builder()
                        .year(year)
                        .quarter(quarter)
                        .form("KIS_REPORT")
                        .report(statements)
                        .build());
            }
        }

        // 시간 순 정렬 (성장률 계산용)
        periodDataList.sort((a, b) -> {
            if (a.getYear() != b.getYear()) return a.getYear() - b.getYear();
            return a.getQuarter() - b.getQuarter();
        });

        // 성장률(QoQ) 계산
        for (FinancialPeriodData curr : periodDataList) {
            FinancialPeriodData prev = findPrevQuarter(periodDataList, curr.getYear(), curr.getQuarter());

            if (prev != null && curr.getReport() != null && prev.getReport() != null) {
                IncomeStatement prevIs = prev.getReport().getIncomeStatement();
                IncomeStatement currIs = curr.getReport().getIncomeStatement();

                curr.setRevenueGrowth(calculateGrowthRate(currIs.getRevenue(), prevIs.getRevenue()));
                curr.setOperatingIncomeGrowth(calculateGrowthRate(currIs.getOperatingIncome(), prevIs.getOperatingIncome()));
                curr.setNetIncomeGrowth(calculateGrowthRate(currIs.getNetIncomeLoss(), prevIs.getNetIncomeLoss()));
            }
        }

        // 최신순 정렬 (UI 노출용)
        periodDataList.sort((a, b) -> {
            if (a.getYear() != b.getYear()) return b.getYear() - a.getYear();
            return b.getQuarter() - a.getQuarter();
        });

        // 재무 지표(Indicators) 매핑
        FinancialIndicators indicators = FinancialIndicators.createFallback();
        if (indicatorsOutputs != null && !indicatorsOutputs.isEmpty()) {
            KisIndicatorsOutput latestInd = indicatorsOutputs.get(0);
            indicators = FinancialIndicators.builder()
                    .eps(toBigDecimal(latestInd.getEps()))
                    .roe(toBigDecimal(latestInd.getRoe()))
                    .debtRatio(toBigDecimal(latestInd.getDebtRatio()))
                    .currentRatio(toBigDecimal(latestInd.getCurrentRatio()))
                    .revenueGrowth(toBigDecimal(latestInd.getRevenueGrowth()))
                    .build();
        }

        return StockFinancialsResponse.builder()
                .data(periodDataList)
                .indicators(indicators)
                .build();
    }


    private List<KisBalanceSheetOutput> callBalanceSheetsApi(String symbol) {
        KisResponseV1<List<KisBalanceSheetOutput>> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/finance/balance-sheet")
                        .queryParam("FID_DIV_CLS_CODE", "1")
                        .queryParam("fid_cond_mrkt_div_code", "J")
                        .queryParam("fid_input_iscd", symbol)
                        .build()
                )
                .headers(h -> {
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", "FHKST66430100");
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<KisResponseV1<List<KisBalanceSheetOutput>>>() {
                })
                .block();
        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput() : null;
    }

    private List<KisIncomeStatementOutput> callIncomeStatementApi(String symbol) {
        KisResponseV1<List<KisIncomeStatementOutput>> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/finance/income-statement")
                        .queryParam("FID_DIV_CLS_CODE", "1")
                        .queryParam("fid_cond_mrkt_div_code", "J")
                        .queryParam("fid_input_iscd", symbol)
                        .build()
                )
                .headers(h -> {
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", "FHKST66430200");
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<KisResponseV1<List<KisIncomeStatementOutput>>>() {
                })
                .block();
        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput() : null;
    }

    private List<KisIndicatorsOutput> callIndicatorsOutput(String symbol) {
        KisResponseV1<List<KisIndicatorsOutput>> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/uapi/domestic-stock/v1/finance/financial-ratio")
                        .queryParam("FID_DIV_CLS_CODE", "1")
                        .queryParam("fid_cond_mrkt_div_code", "J")
                        .queryParam("fid_input_iscd", symbol)
                        .build()
                )
                .headers(h -> {
                    h.add("authorization", "Bearer " + tokenManager.getAccessToken());
                    h.add("appkey", properties.appKey());
                    h.add("appsecret", properties.appSecret());
                    h.add("tr_id", "FHKST66430300");
                })
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<KisResponseV1<List<KisIndicatorsOutput>>>() {
                })
                .block();
        return (response != null && "0".equals(response.getReturnCode())) ? response.getOutput() : null;
    }

    private BigDecimal calculateGrowthRate(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    private BigDecimal toBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }


    // 전분기(QoQ) 데이터 찾기
    private FinancialPeriodData findPrevQuarter(List<FinancialPeriodData> list, int currentYear, int currentQuarter) {
        int targetYear = currentQuarter == 1 ? currentYear - 1 : currentYear;
        int targetQuarter = currentQuarter == 1 ? 4 : currentQuarter - 1;

        return list.stream()
                .filter(d -> d.getYear() == targetYear && d.getQuarter() == targetQuarter)
                .findFirst()
                .orElse(null);
    }

    // 전년 동기(YoY) 데이터 찾기
    private FinancialPeriodData findSameQuarterLastYear(List<FinancialPeriodData> list, int currentYear, int currentQuarter) {
        return list.stream()
                .filter(d -> d.getYear() == currentYear - 1 && d.getQuarter() == currentQuarter)
                .findFirst()
                .orElse(null);
    }



}