package com.stocklog.modules.market.service;

import com.stocklog.modules.market.index.dto.IndexDto;
import com.stocklog.modules.market.index.dto.IndexDashboardDto;
import com.stocklog.modules.market.index.dto.IndexChartDto;
import com.stocklog.modules.market.index.entity.MarketIndex;
import com.stocklog.modules.market.index.entity.MarketIndexHistory;
import com.stocklog.modules.market.index.provider.IndexChartProvider;
import com.stocklog.modules.market.index.repository.MarketIndexHistoryRepository;
import com.stocklog.modules.market.index.repository.MarketIndexRepository;
import com.stocklog.modules.market.domain.type.IndexType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

    private final IndexChartProvider provider;
    private final MarketIndexRepository marketIndexRepository;
    private final MarketIndexHistoryRepository marketIndexHistoryRepository;

    // 자기 호출 시 @Transactional 프록시를 타도록 자신을 주입
    @Autowired
    @Lazy
    private IndexService self;

    // 대시보드 조회
    @Transactional(readOnly = true)
    public IndexDashboardDto getMarketDashboard() {
        // 사용자가 화면을 열 때는 외부 API를 절대 쏘지 않고, DB에 캐싱된 스냅샷만 즉시 읽어옵니다
        return getMarketDashboardDto();
    }


    // 내부 DB 조회 유틸리티
    public IndexDto getIndexDtoFromDb(String seriesId) {
        MarketIndex marketIndex = marketIndexRepository.findById(seriesId).orElse(null);
        List<MarketIndexHistory> histories = marketIndexHistoryRepository.findBySymbolOrderByDateAsc(seriesId);

        List<IndexChartDto> chartDataList = getIndexChartDataList(histories);

        return IndexDto.builder()
                .seriesId(seriesId)
                .data(chartDataList)
                .changeValue(marketIndex != null && marketIndex.getChangeValue() != null ? marketIndex.getChangeValue() : BigDecimal.ZERO)
                .changeRate(marketIndex != null && marketIndex.getChangeRate() != null ? marketIndex.getChangeRate() : BigDecimal.ZERO)
                .build();
    }

    // DB 저장 로직 (트랜잭션 고립)

    @Transactional
    public void saveIndexHistory(IndexDto indexDto) {
        String seriesId = indexDto.seriesId();
        List<IndexChartDto> indexChartData = indexDto.data();

        if (indexChartData == null || indexChartData.size() < 2) {
            return;
        }

        LocalDate latestSavedDate = marketIndexHistoryRepository.findMaxDateBySymbol(seriesId)
                .orElse(LocalDate.of(1900, 1, 1));


        List<MarketIndexHistory> newHistories = indexChartData.stream()
                .filter(data -> data.date().isAfter(latestSavedDate))
                .map(data -> MarketIndexHistory.builder()
                        .symbol(seriesId)
                        .date(data.date())
                        .closeValue(data.close())
                        .build())
                .collect(Collectors.toList());

        if (!newHistories.isEmpty()) {
            marketIndexHistoryRepository.saveAll(newHistories);
            log.info("[{}] {}건의 신규 이력 데이터 DB 적재 완료", seriesId, newHistories.size());
        }

        int lastIdx = indexChartData.size() - 1;
        BigDecimal todayValue = indexChartData.get(lastIdx).close();
        BigDecimal yesterdayValue = indexChartData.get(lastIdx - 1).close();

        BigDecimal changeValue = getChangeValue(todayValue, yesterdayValue);
        BigDecimal changeRate = getChangeRate(todayValue, yesterdayValue);

        MarketIndex marketIndex = marketIndexRepository.findById(seriesId)
                .orElse(MarketIndex.builder()
                        .symbol(seriesId)
                        .name(IndexType.fromSeriesId(seriesId).getDisplayName())
                        .indexType(IndexType.fromSeriesId(seriesId))
                        .build());

        marketIndex.update(todayValue, changeValue, changeRate);
        marketIndexRepository.save(marketIndex);
    }
    // 백그라운드 데이터 동기화 스케줄러
    @Scheduled(cron = "0 7 * * * *")
    public void syncAllMarketIndices() {
        log.info("[Index Sync] 전체 시장 지수 백그라운드 동기화 시작");

        for (IndexType type : IndexType.values()) {
            try {
                // API Rate Limit 방어를 위한 대기
                Thread.sleep(300);

                IndexDto apiResponse = provider.fetchIndexResponse(type);

                // @Transactional 프록시를 정상적으로 타기 위해 self 객체로 저장 로직 호출
                self.saveIndexHistory(apiResponse);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[Index Sync] 스레드 대기 중단됨: {}", e.getMessage());
            } catch (Exception e) {
                log.error("[Index Sync] [{}] 동기화 실패: {}", type.getSeriesId(), e.getMessage());
            }
        }
        log.info("[Index Sync] 전체 시장 지수 동기화 완료");
    }
    private IndexDashboardDto getMarketDashboardDto() {
        return IndexDashboardDto.builder()
                // 글로벌 환율 / 달러
                .usdJpy(getIndexDtoFromDb(IndexType.USD_JPY.getSeriesId()))
                .dollarIndex(getIndexDtoFromDb(IndexType.DOLLAR_INDEX.getSeriesId()))

                // 미국 전체 시장 대표 지수
                .sp500(getIndexDtoFromDb(IndexType.SP500.getSeriesId()))
                .nasdaq100(getIndexDtoFromDb(IndexType.NASDAQ_100.getSeriesId()))
                .dowJones(getIndexDtoFromDb(IndexType.DOW_JONES.getSeriesId()))

                // 시장 변동성 및 신용 리스크
                .vix(getIndexDtoFromDb(IndexType.VIX.getSeriesId()))
                .highYieldSpread(getIndexDtoFromDb(IndexType.HIGH_YIELD_SPREAD.getSeriesId()))
                .financialStressIndex(getIndexDtoFromDb(IndexType.FINANCIAL_STRESS.getSeriesId()))

                // 채권/금리
                .us10y(getIndexDtoFromDb(IndexType.US_TREASURY_10Y.getSeriesId()))
                .us2y(getIndexDtoFromDb(IndexType.US_TREASURY_2Y.getSeriesId()))
                .yieldCurve10y2y(getIndexDtoFromDb(IndexType.T10Y2Y.getSeriesId()))
                .fedFundsRate(getIndexDtoFromDb(IndexType.US_FED_FUNDS_RATE.getSeriesId()))

                // 원자재 / 대체자산
                .wti(getIndexDtoFromDb(IndexType.WTI_CRUDE_OIL.getSeriesId()))
                .bitcoin(getIndexDtoFromDb(IndexType.BITCOIN.getSeriesId()))

                // 거시 물가
                .cpi(getIndexDtoFromDb(IndexType.US_CPI.getSeriesId()))
                .corePce(getIndexDtoFromDb(IndexType.US_CORE_PCE.getSeriesId()))

                // 고용
                .unemploymentRate(getIndexDtoFromDb(IndexType.US_UNEMPLOYMENT_RATE.getSeriesId()))
                .nonfarmPayrolls(getIndexDtoFromDb(IndexType.US_NONFARM_PAYROLLS.getSeriesId()))
                .initialClaims(getIndexDtoFromDb(IndexType.US_INITIAL_CLAIMS.getSeriesId()))

                // 경기 및 소비
                .gdp(getIndexDtoFromDb(IndexType.US_GDP.getSeriesId()))
                .retailSales(getIndexDtoFromDb(IndexType.US_RETAIL_SALES.getSeriesId()))
                .consumerSentiment(getIndexDtoFromDb(IndexType.US_CONSUMER_SENTIMENT.getSeriesId()))
                .consumerRevolvingLoans(getIndexDtoFromDb(IndexType.US_CONSUMER_REVOLVING_LOANS.getSeriesId()))

                // 유동성
                .fedTotalAssets(getIndexDtoFromDb(IndexType.FED_TOTAL_ASSETS.getSeriesId()))
                .reverseRepo(getIndexDtoFromDb(IndexType.REVERSE_REPO.getSeriesId()))
                .m2(getIndexDtoFromDb(IndexType.US_M2.getSeriesId()))

                // 반도체/AI 핵심
                .phlxSemi(getIndexDtoFromDb(IndexType.PHLX_SEMI.getSeriesId()))
                .nasdaqAiBigdata(getIndexDtoFromDb(IndexType.NASDAQ_AI_BIGDATA.getSeriesId()))
                .nasdaqCapexAchievers(getIndexDtoFromDb(IndexType.NASDAQ_CAPEX_ACHIEVERS.getSeriesId()))
                .indSemi(getIndexDtoFromDb(IndexType.IND_SEMI.getSeriesId()))

                // 기타 섹터
                .nasdaqIseCybersecurity(getIndexDtoFromDb(IndexType.NASDAQ_ISE_CYBERSECURITY.getSeriesId()))
                .nasdaqBiotech(getIndexDtoFromDb(IndexType.NASDAQ_BIOTECH.getSeriesId()))
                .nasdaqConsumerStaples(getIndexDtoFromDb(IndexType.NASDAQ_CONSUMER_STAPLES.getSeriesId()))
                .nasdaqCleanEnergy(getIndexDtoFromDb(IndexType.NASDAQ_CLEAN_ENERGY.getSeriesId()))
                .phlxGoldSilver(getIndexDtoFromDb(IndexType.PHLX_GOLD_SILVER.getSeriesId()))
                .sectorDiscretionaryDurable(getIndexDtoFromDb(IndexType.SECTOR_DISCRETIONARY_DURABLE.getSeriesId()))
                .sectorFinancials(getIndexDtoFromDb(IndexType.SECTOR_FINANCIALS.getSeriesId()))
                .sectorRealEstate(getIndexDtoFromDb(IndexType.SECTOR_REAL_ESTATE.getSeriesId()))
                .sectorIndustrials(getIndexDtoFromDb(IndexType.SECTOR_INDUSTRIALS.getSeriesId()))
                .usIndpro(getIndexDtoFromDb(IndexType.US_INDPRO.getSeriesId()))
                .build();
    }

    private static List<IndexChartDto> getIndexChartDataList(List<MarketIndexHistory> histories) {
        return histories.stream()
                .map(history -> IndexChartDto.builder()
                        .date(history.getDate())
                        .close(history.getCloseValue())
                        .build())
                .collect(Collectors.toList());
    }

    private BigDecimal getChangeValue(BigDecimal today, BigDecimal yesterday) {
        if (today == null || yesterday == null) return BigDecimal.ZERO;
        return today.subtract(yesterday);
    }

    private BigDecimal getChangeRate(BigDecimal today, BigDecimal yesterday) {
        if (today == null || yesterday == null || yesterday.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return today.subtract(yesterday)
                .divide(yesterday, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
}