package com.stocklog.domain.stock.service;

import com.stocklog.domain.stock.analysis.aggregator.ScoreAggregator;
import com.stocklog.domain.stock.analysis.domain.valuation.CorporateValueAnalyzer;
import com.stocklog.domain.stock.analysis.domain.signal.EntrySignalAnalyzer;
import com.stocklog.domain.stock.analysis.domain.momentum.MomentumAnalyzer;
import com.stocklog.domain.stock.analysis.domain.technical.TechnicalAnalyzer;
import com.stocklog.domain.stock.analysis.result.StockAnalysisResult;
import com.stocklog.domain.stock.analysis.domain.momentum.MomentumScore;
import com.stocklog.domain.stock.analysis.domain.technical.TechnicalScore;
import com.stocklog.domain.stock.analysis.domain.valuation.ValuationScore;
import com.stocklog.domain.stock.analysis.domain.signal.EntrySignal;
import com.stocklog.domain.stock.dto.response.StockEarningResponse;
import com.stocklog.domain.stock.dto.response.StockFinancialsResponse;
import com.stocklog.domain.stock.dto.vo.analysis.StockSnapshot;
import com.stocklog.domain.stock.dto.vo.fundamental.financial.FinancialIndicators;
import com.stocklog.domain.stock.dto.vo.info.StockInfoDetail;
import com.stocklog.domain.stock.dto.vo.price.StockChartData;
import com.stocklog.domain.stock.dto.vo.price.StockPriceDetail;
import com.stocklog.domain.stock.entity.Stock;
import com.stocklog.domain.stock.provider.StockFinanceProvider;
import com.stocklog.domain.stock.type.Nation;
import com.stocklog.domain.stock.type.Period;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAnalysisService {

    private final StockService      stockService;
    private final StockInfoService  stockInfoService;
    private final StockPriceService stockPriceService;
    private final StockChartService stockChartService;

    private final CorporateValueAnalyzer corporateValueAnalyzer;
    private final MomentumAnalyzer momentumAnalyzer;
    private final TechnicalAnalyzer technicalAnalyzer;
    private final ScoreAggregator   scoreAggregator;
    private final EntrySignalAnalyzer entrySignalAnalyzer;

    private final List<StockFinanceProvider> providers;

    private static final Set<String> ETF_SECTORS = Set.of(
            "순수 지수형 ETF", "테마·섹터형 ETF", "채권·현금성 ETF"
    );

    // ── [1] 메인 분석 오케스트레이션 ───────────────────────────────────────────
    public StockAnalysisResult analyze(String code) {
        // 1. 기초 데이터 페칭
        Stock stock = stockService.getStock(code);
        StockInfoDetail info = stockInfoService.fetchInfoDetail(code);
        StockPriceDetail price = stockPriceService.fetchPriceDetail(code);

        // 2. 재무 데이터 페칭 및 지표 계산 (복잡한 로직 위임)
        StockFinancialsResponse finData = this.fetchFinancial(code);
        if (finData == null) {
            finData = StockFinancialsResponse.createFallback();
        }

        FinancialIndicators indicators = finData.indicators();
        if (indicators != null && price != null) {
            enrichFinancialIndicators(stock, price, finData, indicators);
        }

        // 3. 차트 및 스냅샷 생성
        StockSnapshot snapshot = buildSnapshot(stock, info, price);
        boolean isEtf = ETF_SECTORS.contains(stock.getSector());

        List<StockChartData> weeklyChart = stockChartService.getChartData(code, Period.WEEK);
        List<StockChartData> dailyChart = stockChartService.getChartData(code, Period.DAY);

        // 4. 도메인별 분석 실행
        ValuationScore corporateValue = corporateValueAnalyzer.analyze(snapshot, indicators, isEtf);
        MomentumScore momentum = momentumAnalyzer.analyze(snapshot);
        TechnicalScore technical = technicalAnalyzer.analyze(weeklyChart);
        EntrySignal entry = entrySignalAnalyzer.analyze(dailyChart);

        // 5. 결과 합산
        return scoreAggregator.aggregate(snapshot, corporateValue, momentum, technical, entry, finData, isEtf);
    }

    // ── [2] 재무 지표 계산 및 보강 (복잡도 격리) ────────────────────────────────
    private void enrichFinancialIndicators(Stock stock, StockPriceDetail price,
                                           StockFinancialsResponse finData, FinancialIndicators indicators) {

        // 기본 가격 지표 매핑
        indicators.setPer(price.getPer());
        indicators.setPbr(price.getPbr());
        indicators.setEps(price.getEps());
        indicators.setBps(price.getBps());

        if (finData.data() == null || finData.data().isEmpty()) return;

        var latest = finData.data().get(0);
        var bs = latest.getReport().getBalanceSheet();
        var is = latest.getReport().getIncomeStatement();

        // [부채비율 & 유동비율 계산]
        indicators.setDebtRatio(calculateRatioPercent(bs.getLiabilities(), bs.getStockholdersEquity()));
        indicators.setCurrentRatio(calculateRatioPercent(bs.getCurrentAssets(), bs.getCurrentLiabilities()));

        // [영업이익률(Operating Margin) 계산 - ✨ 신규 추가]
        indicators.setOperatingMargin(calculateRatioPercent(is.getOperatingIncome(), is.getRevenue()));

        // [TTM 기반 데이터 합산 (최근 4개 분기)]
        BigDecimal ttmNetIncome = finData.data().stream()
                .limit(4).map(d -> d.getReport().getIncomeStatement().getNetIncomeLoss())
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ttmRevenue = finData.data().stream()
                .limit(4).map(d -> d.getReport().getIncomeStatement().getRevenue())
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        // [ROE 계산]
        indicators.setRoe(calculateRatioPercent(ttmNetIncome, bs.getStockholdersEquity()));

        // [PSR 계산]
        if (ttmRevenue.compareTo(BigDecimal.ZERO) > 0 && price.getMarketCap() != null) {
            BigDecimal marketCap = price.getMarketCap();
            if (stock.getNation() == Nation.KR && marketCap.compareTo(ttmRevenue) < 0) {
                marketCap = marketCap.multiply(new BigDecimal("100000000")); // 한국 시총 보정
            }
            indicators.setPsr(marketCap.divide(ttmRevenue, 2, RoundingMode.HALF_UP));
        }

        // [매출성장률 YoY 계산]
        var prevYearData = finData.data().stream()
                .filter(d -> d.getYear() == latest.getYear() - 1 && d.getQuarter() == latest.getQuarter())
                .findFirst().orElse(null);

        if (prevYearData != null) {
            BigDecimal currentRev = is.getRevenue();
            BigDecimal pastRev = prevYearData.getReport().getIncomeStatement().getRevenue();

            if (currentRev != null && pastRev != null && pastRev.compareTo(BigDecimal.ZERO) != 0) {
                indicators.setRevenueGrowth(currentRev.subtract(pastRev)
                        .divide(pastRev.abs(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")));
            }
        } else {
            indicators.setRevenueGrowth(latest.getRevenueGrowth());
        }
    }

    // ── [3] 유틸리티 및 페칭 메서드 ──────────────────────────────────────────

    /**
     * (분자 / 분모) * 100 을 안전하게 계산하는 헬퍼 메서드
     */
    private BigDecimal calculateRatioPercent(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
    }

    private StockSnapshot buildSnapshot(Stock stock, StockInfoDetail info, StockPriceDetail price) {
        return StockSnapshot.builder()
                .code(stock.getCode()).name(stock.getName())
                .nation(stock.getNation()).market(stock.getMarketType()).currency(stock.getCurrency())
                .sector(stock.getSector())
                .indexLarge(info.getIndexLarge()).indexMedium(info.getIndexMedium()).indexSmall(info.getIndexSmall())
                .parValue(info.getParValue()).isTradeStop(info.getIsTradeStop()).isAdminItem(info.getIsAdminItem())
                .per(price.getPer()).pbr(price.getPbr()).eps(price.getEps()).bps(price.getBps())
                .currentPrice(price.getCurrentPrice())
                .marketCap(price.getMarketCap()).volume(price.getVolume())
                .w52High(price.getW52High()).w52Low(price.getW52Low())
                .build();
    }

    public StockFinancialsResponse fetchFinancial(String code) {
        Stock stock = stockService.getStock(code);
        return providers.stream()
                .filter(p -> p.supports(stock.getMarketType()))
                .findFirst()
                .map(provider -> provider.getFinancialStatements(code))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다: " + code));
    }

    public StockEarningResponse fetchEarning(String code) {
        Stock stock = stockService.getStock(code);
        return providers.stream()
                .filter(p -> p.supports(stock.getMarketType()))
                .findFirst()
                .map(provider -> provider.getEarningsSurprises(code))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 종목입니다: " + code));
    }
}