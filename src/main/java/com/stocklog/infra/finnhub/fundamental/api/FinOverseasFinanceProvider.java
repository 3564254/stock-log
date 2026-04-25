package com.stocklog.infra.finnhub.fundamental.api;

import com.stocklog.domain.stock.dto.response.StockEarningResponse;
import com.stocklog.domain.stock.dto.response.StockFinancialsResponse;
import com.stocklog.domain.stock.dto.vo.fundamental.earning.EarningSurpriseData;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.*;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.FinancialStatements;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail.BalanceSheet;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail.CashFlow;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.statement.detail.IncomeStatement;
import com.stocklog.domain.stock.provider.StockFinanceProvider;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.infra.finnhub.config.FinProperties;
import com.stocklog.infra.finnhub.fundamental.dto.FinEarningsSurpriseOutput;
import com.stocklog.infra.finnhub.fundamental.dto.FinStockFinancialsOutput;
import com.stocklog.infra.finnhub.fundamental.dto.FinStockFinancialsOutput.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public StockFinancialsResponse getFinancialStatements(String symbol) {
        log.info("FIN 해외 Financials [{}]", symbol);
        try {
            FinStockFinancialsOutput output = callFinancialsApi(symbol);
            if (output == null) return StockFinancialsResponse.createFallback();
            return getFinancialResponse(output);
        } catch (Exception e) {
            log.info("FIN 해외 Financials 정보 조회 중 오류 {}", e.getMessage());
        }
        return StockFinancialsResponse.createFallback();
    }

    @Override
    public StockEarningResponse getEarningsSurprises(String symbol) {
        log.info("FIN 해외 Earnings 상세 조회");
        try {
            List<FinEarningsSurpriseOutput> earningsSurpriseOutputs = callEarningApi(symbol);
            if (earningsSurpriseOutputs == null) return StockEarningResponse.createFallback();
            return getEarningResponse(earningsSurpriseOutputs);
        } catch (Exception e) {
            log.info("FIN 해외 Earnings 정보 조회 중 오류 {}", e.getMessage());
        }
        return StockEarningResponse.createFallback();
    }

    @Override
    public boolean supports(MarketType marketType) {
        return marketType.isOverseas();
    }

    private FinStockFinancialsOutput callFinancialsApi(String symbol) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusYears(4);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/stock/financials-reported")
                        .queryParam("symbol", symbol)
                        .queryParam("freq", Frequency.quarterly.name())
                        .queryParam("from", startDate)
                        .queryParam("to", endDate)
                        .build()
                )
                .header("X-Finnhub-Token", finProperties.getApiKey())
                .retrieve()
                .bodyToMono(FinStockFinancialsOutput.class)
                .block();
    }

    private List<FinEarningsSurpriseOutput> callEarningApi(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/stock/earnings")
                        .queryParam("symbol", symbol)
                        .build()
                )
                .header("X-Finnhub-Token", finProperties.getApiKey())
                .retrieve()
                .bodyToFlux(FinEarningsSurpriseOutput.class)
                .collectList()
                .block();
    }

    private StockFinancialsResponse getFinancialResponse(FinStockFinancialsOutput output) {
        if (output.getData() == null || output.getData().isEmpty()) {
            return StockFinancialsResponse.createFallback();
        }

        List<FinStockFinancialsOutput.FinFinancialPeriodData> sortedData = new ArrayList<>(output.getData());
        sortedData.sort((a, b) -> {
            if (a.getYear() != b.getYear()) return a.getYear() - b.getYear();
            return a.getQuarter() - b.getQuarter();
        });

        Map<String, IncomeStatement> ytdIsMap = new HashMap<>();
        Map<String, CashFlow> ytdCfMap = new HashMap<>();

        for (FinStockFinancialsOutput.FinFinancialPeriodData periodData : sortedData) {
            if (periodData.getReport() == null) continue;
            FinFinancialStatements report = periodData.getReport();
            String periodKey = periodData.getYear() + "-" + periodData.getQuarter();

            IncomeStatement ytdIs = IncomeStatement.builder()
                    .grossProfit(extractValue(report.getIncomeStatement(), "us-gaap_GrossProfit"))
                    .netIncomeLoss(extractValue(report.getIncomeStatement(), "us-gaap_NetIncomeLoss"))
                    .operatingExpenses(extractValue(report.getIncomeStatement(), "us-gaap_OperatingExpenses"))
                    .operatingIncome(extractValue(report.getIncomeStatement(), "us-gaap_OperatingIncomeLoss"))
                    .revenue(extractRevenue(report.getIncomeStatement()))
                    .epsBasic(extractValue(report.getIncomeStatement(), "us-gaap_EarningsPerShareBasic"))
                    .epsDiluted(extractValue(report.getIncomeStatement(), "us-gaap_EarningsPerShareDiluted"))
                    .build();
            ytdIsMap.put(periodKey, ytdIs);

            CashFlow ytdCf = CashFlow.builder()
                    .netIncomeLoss(extractValue(report.getCashFlow(), "us-gaap_NetIncomeLoss"))
                    .operatingCashFlow(extractValue(report.getCashFlow(), "us-gaap_NetCashProvidedByUsedInOperatingActivities"))
                    .capex(extractValue(report.getCashFlow(), "us-gaap_PaymentsToAcquirePropertyPlantAndEquipment"))
                    .build();
            ytdCfMap.put(periodKey, ytdCf);
        }

        List<FinancialPeriodData> financialPeriodDataList = new ArrayList<>();

        for (FinStockFinancialsOutput.FinFinancialPeriodData periodData : sortedData) {
            if (periodData.getReport() == null) continue;
            FinFinancialStatements report = periodData.getReport();
            int year = periodData.getYear();
            int quarter = periodData.getQuarter();
            String periodKey = year + "-" + quarter;

            BalanceSheet balanceSheet = BalanceSheet.builder()
                    .assets(extractValue(report.getBalanceSheet(), "us-gaap_Assets"))
                    .liabilities(extractValue(report.getBalanceSheet(), "us-gaap_Liabilities"))
                    .stockholdersEquity(extractValue(report.getBalanceSheet(), "us-gaap_StockholdersEquity"))
                    .currentAssets(extractValue(report.getBalanceSheet(), "us-gaap_AssetsCurrent"))
                    .currentLiabilities(extractValue(report.getBalanceSheet(), "us-gaap_LiabilitiesCurrent"))
                    .build();

            IncomeStatement currentIsYtd = ytdIsMap.get(periodKey);
            CashFlow currentCfYtd = ytdCfMap.get(periodKey);

            IncomeStatement finalIs = currentIsYtd;
            CashFlow finalCf = currentCfYtd;
            boolean isCalculated = true; // ✨ 계산 성공 플래그 추가

            if (quarter > 1 && currentIsYtd != null && currentCfYtd != null) {
                IncomeStatement prevIsYtd = ytdIsMap.get(year + "-" + (quarter - 1));
                CashFlow prevCfYtd = ytdCfMap.get(year + "-" + (quarter - 1));

                if (prevIsYtd != null && prevCfYtd != null) {
                    finalIs = IncomeStatement.builder()
                            .grossProfit(currentIsYtd.getGrossProfit().subtract(prevIsYtd.getGrossProfit()))
                            .netIncomeLoss(currentIsYtd.getNetIncomeLoss().subtract(prevIsYtd.getNetIncomeLoss()))
                            .operatingExpenses(currentIsYtd.getOperatingExpenses().subtract(prevIsYtd.getOperatingExpenses()))
                            .operatingIncome(currentIsYtd.getOperatingIncome().subtract(prevIsYtd.getOperatingIncome()))
                            .revenue(currentIsYtd.getRevenue().subtract(prevIsYtd.getRevenue()))
                            .epsBasic(currentIsYtd.getEpsBasic())
                            .epsDiluted(currentIsYtd.getEpsDiluted())
                            .build();

                    finalCf = CashFlow.builder()
                            .netIncomeLoss(currentCfYtd.getNetIncomeLoss().subtract(prevCfYtd.getNetIncomeLoss()))
                            .operatingCashFlow(currentCfYtd.getOperatingCashFlow().subtract(prevCfYtd.getOperatingCashFlow()))
                            .capex(currentCfYtd.getCapex().subtract(prevCfYtd.getCapex()))
                            .build();
                } else {
                    isCalculated = false;
                }
            }

            if (isCalculated && finalIs != null && finalCf != null) {
                financialPeriodDataList.add(FinancialPeriodData.builder()
                        .year(year)
                        .quarter(quarter)
                        .form(periodData.getForm())
                        .startDate(periodData.getStartDate())
                        .endDate(periodData.getEndDate())
                        .report(FinancialStatements.builder()
                                .balanceSheet(balanceSheet)
                                .incomeStatement(finalIs)
                                .cashFlow(finalCf)
                                .build())
                        .build());
            }
        }

        // ✨ [수정] 증감률 계산 로직 (오름차순 상태에서 직전 분기와 비교)
        for (FinancialPeriodData curr : financialPeriodDataList) {
            // 무식하게 바로 앞 인덱스(i-1)를 믿지 않고, 정확히 전분기를 찾습니다.
            FinancialPeriodData prev = findPrevQuarter(financialPeriodDataList, curr.getYear(), curr.getQuarter());

            if (prev != null && curr.getReport() != null && prev.getReport() != null) {
                IncomeStatement prevIs = prev.getReport().getIncomeStatement();
                IncomeStatement currIs = curr.getReport().getIncomeStatement();

                curr.setRevenueGrowth(calculateGrowthRate(currIs.getRevenue(), prevIs.getRevenue()));
                curr.setOperatingIncomeGrowth(calculateGrowthRate(currIs.getOperatingIncome(), prevIs.getOperatingIncome()));
                curr.setNetIncomeGrowth(calculateGrowthRate(currIs.getNetIncomeLoss(), prevIs.getNetIncomeLoss()));
            }
        }

        // 4. 프론트엔드 출력을 위해 다시 최신순(내림차순)으로 정렬
        financialPeriodDataList.sort((a, b) -> {
            if (a.getYear() != b.getYear()) return b.getYear() - a.getYear();
            return b.getQuarter() - a.getQuarter();
        });

        FinancialIndicators indicators = getIndicators(financialPeriodDataList);

        return StockFinancialsResponse.builder()
                .code(output.getSymbol())
                .data(financialPeriodDataList)
                .indicators(indicators)
                .build();
    }

    private BigDecimal calculateGrowthRate(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    private StockEarningResponse getEarningResponse(List<FinEarningsSurpriseOutput> outputs) {
        List<EarningSurpriseData> data = new ArrayList<>();
        for (FinEarningsSurpriseOutput output : outputs) {
            EarningSurpriseData surpriseData = EarningSurpriseData.builder()
                    .actual(output.getActual())
                    .estimate(output.getEstimate())
                    .period(output.getPeriod())
                    .quarter(output.getQuarter())
                    .surprise(output.getSurprise())
                    .surprisePercent(output.getSurprisePercent())
                    .symbol(output.getSymbol())
                    .year(output.getYear())
                    .build();
            data.add(surpriseData);
        }
        return StockEarningResponse.builder()
                .data(data)
                .build();
    }

    private FinancialIndicators getIndicators(List<FinancialPeriodData> financialPeriodDataList) {
        if (financialPeriodDataList == null || financialPeriodDataList.isEmpty()) {
            return FinancialIndicators.createFallback();
        }

        FinancialPeriodData latest = financialPeriodDataList.get(0);
        BalanceSheet bs = latest.getReport().getBalanceSheet();
        IncomeStatement is = latest.getReport().getIncomeStatement();

        BigDecimal roe = BigDecimal.ZERO;
        if (bs.getStockholdersEquity() != null && bs.getStockholdersEquity().compareTo(BigDecimal.ZERO) != 0) {
            roe = is.getNetIncomeLoss()
                    .divide(bs.getStockholdersEquity(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        BigDecimal debtRatio = BigDecimal.ZERO;
        if (bs.getStockholdersEquity() != null && bs.getStockholdersEquity().compareTo(BigDecimal.ZERO) != 0) {
            debtRatio = bs.getLiabilities()
                    .divide(bs.getStockholdersEquity(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        BigDecimal currentRatio = BigDecimal.ZERO;
        if (bs.getCurrentLiabilities() != null && bs.getCurrentLiabilities().compareTo(BigDecimal.ZERO) != 0) {
            currentRatio = bs.getCurrentAssets()
                    .divide(bs.getCurrentLiabilities(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        BigDecimal revGrowth = null;
        if (!financialPeriodDataList.isEmpty()) {
            BigDecimal currentRev = is.getRevenue();

            FinancialPeriodData pastData = findSameQuarterLastYear(financialPeriodDataList, latest.getYear(), latest.getQuarter());

            if (pastData != null && pastData.getReport() != null && pastData.getReport().getIncomeStatement() != null) {
                BigDecimal pastRev = pastData.getReport().getIncomeStatement().getRevenue();

                if (currentRev != null && pastRev != null && pastRev.compareTo(BigDecimal.ZERO) != 0) {
                    revGrowth = currentRev.subtract(pastRev)
                            .divide(pastRev.abs(), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                }
            }
        }
        return FinancialIndicators.builder()
                .eps(is.getEpsDiluted() != null ? is.getEpsDiluted() : BigDecimal.ZERO)
                .roe(roe)
                .debtRatio(debtRatio)
                .currentRatio(currentRatio)
                .revenueGrowth(revGrowth)
                .build();
    }
    private BigDecimal extractValue(List<FinReportItem> items, String targetConcept) {
        if (items == null || items.isEmpty()) return BigDecimal.ZERO;
        return items.stream()
                .filter(item -> targetConcept.equals(item.getConcept()))
                .map(FinReportItem::getValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal extractRevenue(List<FinReportItem> items) {
        BigDecimal rev = extractValue(items, "us-gaap_RevenueFromContractWithCustomerExcludingAssessedTax");
        if (rev.compareTo(BigDecimal.ZERO) == 0) {
            rev = extractValue(items, "us-gaap_SalesRevenueNet");
        }
        if (rev.compareTo(BigDecimal.ZERO) == 0) {
            rev = extractValue(items, "us-gaap_Revenues");
        }
        return rev;
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

    public enum Frequency {
        annual, quarterly
    }
}