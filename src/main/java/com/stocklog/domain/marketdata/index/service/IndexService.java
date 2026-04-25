package com.stocklog.domain.marketdata.index.service;

import com.stocklog.domain.marketdata.analysis.MarketTrendAnalyzer;
import com.stocklog.domain.marketdata.analysis.MarketTrendResult;
import com.stocklog.domain.marketdata.index.dto.IndexResponse;
import com.stocklog.domain.marketdata.index.dto.MarketDashboardResponse;
import com.stocklog.domain.marketdata.index.dto.vo.IndexChartData;
import com.stocklog.domain.marketdata.index.entity.MarketIndex;
import com.stocklog.domain.marketdata.index.entity.MarketIndexHistory;
import com.stocklog.domain.marketdata.index.provider.IndexChartProvider;
import com.stocklog.domain.marketdata.index.repository.MarketIndexHistoryRepository;
import com.stocklog.domain.marketdata.index.repository.MarketIndexRepository;
import com.stocklog.domain.marketdata.index.type.IndexType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexService {

    private final IndexChartProvider provider;
    private final MarketIndexRepository marketIndexRepository;
    private final MarketIndexHistoryRepository marketIndexHistoryRepository;
    private final MarketTrendAnalyzer trendAnalyzer;

    @Transactional(readOnly = true)
    public MarketDashboardResponse getMarketDashboard() {
        // 1. 데이터들 먼저 조회
        MarketDashboardResponse rawDashboard = MarketDashboardResponse.builder()
                .sp500(getIndexResponse(IndexType.SP500.getSeriesId()))
                .nasdaq(getIndexResponse(IndexType.NASDAQ.getSeriesId()))
                .vix(getIndexResponse(IndexType.VIX.getSeriesId()))
                .dxy(getIndexResponse(IndexType.DOLLAR_INDEX.getSeriesId()))
                .usdKrw(getIndexResponse(IndexType.USD_KRW.getSeriesId()))
                .us10y(getIndexResponse(IndexType.US_TREASURY_10Y.getSeriesId()))
                .us2y(getIndexResponse(IndexType.US_TREASURY_2Y.getSeriesId()))
                .wti(getIndexResponse(IndexType.WTI_CRUDE_OIL.getSeriesId()))
                .phlxSemi(getIndexResponse(IndexType.PHLX_SEMI.getSeriesId()))
                .build();

        // 2. 조회된 데이터를 바탕으로 분석 실행
        MarketTrendResult trendResult = trendAnalyzer.analyze(rawDashboard);

        // 3. 분석 결과까지 담아서 최종 반환
        return MarketDashboardResponse.builder()
                .sp500(rawDashboard.sp500())
                .nasdaq(rawDashboard.nasdaq())
                .vix(rawDashboard.vix())
                .dxy(rawDashboard.dxy())
                .usdKrw(rawDashboard.usdKrw())
                .us10y(rawDashboard.us10y())
                .us2y(rawDashboard.us2y())
                .wti(rawDashboard.wti())
                .phlxSemi(rawDashboard.phlxSemi())
                .analysis(trendResult)
                .build();
    }


    @Transactional
    public IndexResponse getIndexResponse(String seriesId) {
        MarketIndex marketIndex = marketIndexRepository.findById(seriesId).orElse(null);
        boolean isUpdatedToday = marketIndex != null
                && marketIndex.getUpdatedAt() != null
                && marketIndex.getUpdatedAt().equals(LocalDate.now());

        if (!isUpdatedToday) {
            log.info("[{}] 최신 데이터 API 호출 및 DB 동기화 시작", seriesId);
            IndexResponse apiResponse = provider.fetchIndexResponse(IndexType.fromSeriesId(seriesId));

            // DB에 데이터 저장 (스냅샷 MarketIndex 도 업데이트 됨)
            saveIndexHistory(apiResponse);

            // ✨ API 응답 데이터 기반으로 당일 등락폭/등락률 계산해서 리턴
            List<IndexChartData> data = apiResponse.data();
            BigDecimal changeValue = BigDecimal.ZERO;
            BigDecimal changeRate = BigDecimal.ZERO;

            if (data != null && data.size() >= 2) {
                BigDecimal today = data.get(data.size() - 1).getValue();
                BigDecimal yesterday = data.get(data.size() - 2).getValue();
                changeValue = getChangeValue(today, yesterday);
                changeRate = getChangeRate(today, yesterday);
            }

            return IndexResponse.builder()
                    .seriesId(seriesId)
                    .data(data)
                    .changeValue(changeValue)
                    .changeRate(changeRate)
                    .build();
        }

        // ✨ 오늘 이미 업데이트 되었다면 DB에서 이력 로드 + 스냅샷에서 등락 정보 가져오기
        log.info("[{}] DB에서 캐싱된 데이터 로드", seriesId);
        List<MarketIndexHistory> histories = marketIndexHistoryRepository.findBySymbolOrderByDateAsc(seriesId);

        List<IndexChartData> chartDataList = histories.stream()
                .map(history -> IndexChartData.builder()
                        .date(history.getDate())
                        .value(history.getCloseValue())
                        .build())
                .collect(Collectors.toList());

        return IndexResponse.builder()
                .seriesId(seriesId)
                .data(chartDataList)
                .changeValue(marketIndex != null ? marketIndex.getChangeValue() : BigDecimal.ZERO)
                .changeRate(marketIndex != null ? marketIndex.getChangeRate() : BigDecimal.ZERO)
                .build();
    }


    @Transactional
    protected void saveIndexHistory(IndexResponse indexResponse) {
        String seriesId = indexResponse.seriesId();
        List<IndexChartData> indexChartData = indexResponse.data();

        if (indexChartData == null || indexChartData.size() < 2) {
            log.warn("[{}] 저장할 데이터가 부족합니다.", seriesId);
            return;
        }

        // 1. 내 DB에 저장된 가장 최근 날짜를 가져옴 (없으면 아주 옛날 날짜 세팅)
        LocalDate latestSavedDate = marketIndexHistoryRepository.findMaxDateBySymbol(seriesId)
                .orElse(LocalDate.of(1900, 1, 1));

        // 2. API 데이터 중, 내 DB에 있는 최신 날짜 '이후'의 데이터만 필터링 (신규 데이터만 쏙 빼냄)
        List<MarketIndexHistory> newHistories = indexChartData.stream()
                .filter(data -> data.getDate().isAfter(latestSavedDate))
                .map(data -> MarketIndexHistory.builder()
                        .symbol(seriesId)
                        .date(data.getDate())
                        .closeValue(data.getValue())
                        .build())
                .collect(Collectors.toList());

        // 3. 신규 데이터가 있을 때만 저장 (이미 다 있으면 아예 save 쿼리가 안 나감)
        if (!newHistories.isEmpty()) {
            marketIndexHistoryRepository.saveAll(newHistories);
            log.info("[{}] {}건의 신규 이력 데이터 저장 완료", seriesId, newHistories.size());
        }

        // 4. (조기 리턴 없이 무조건 실행) 스냅샷(MarketIndex) 최신화 업데이트
        int lastIdx = indexChartData.size() - 1;
        IndexChartData todayData = indexChartData.get(lastIdx);
        IndexChartData yesterdayData = indexChartData.get(lastIdx - 1);

        BigDecimal todayValue = todayData.getValue();
        BigDecimal yesterdayValue = yesterdayData.getValue();

        BigDecimal changeValue = getChangeValue(todayValue, yesterdayValue);
        BigDecimal changeRate = getChangeRate(todayValue, yesterdayValue);

        MarketIndex marketIndex = marketIndexRepository.findById(seriesId).orElse(null);
        if (marketIndex == null) {
            marketIndex = MarketIndex.builder()
                    .symbol(seriesId)
                    .name(seriesId)
                    .indexType(IndexType.fromSeriesId(seriesId))
                    .build();
        }

        marketIndex.update(todayValue, changeValue, changeRate);
        marketIndexRepository.save(marketIndex);
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