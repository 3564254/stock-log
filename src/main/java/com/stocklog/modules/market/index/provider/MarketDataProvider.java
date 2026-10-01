package com.stocklog.modules.market.index.provider;

import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import com.stocklog.modules.market.index.entity.MarketIndexHistory;
import com.stocklog.modules.market.index.repository.MarketIndexHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketDataProvider {

    private final MarketIndexHistoryRepository historyRepository;

    /**
     * Facade에서 호출하는 단일 진입점.
     * 모든 IndexType의 데이터를 1번의 DB 쿼리로 조회 후 분류하여 반환
     */
    public Map<IndexType, MarketChartData> fetchGlobalMarketSnapshot() {
        // 1. 분석에 필요한 전체 심볼 리스트 추출
        List<String> allSymbols = Arrays.stream(IndexType.values())
                .map(IndexType::getSeriesId)
                .collect(Collectors.toList());

        // 2. TrendAnalyzer의 200일선 및 52주 최고가(약 250일) 계산을 위해
        // 넉넉하게 약 3년 전 날짜를 기준일로 설정하여 휴장일 커버
        LocalDate startDate = LocalDate.now().minusYears(3);

        // 3. 단 1번의 쿼리로 전체 데이터 획득 (N+1 문제 원천 차단)
        List<MarketIndexHistory> rawHistories =
                historyRepository.findRecentDataBySymbols(allSymbols, startDate);

        // 4. 조회된 데이터를 메모리에서 IndexType별로 그룹화 (Grouping By)
        Map<IndexType, List<MarketIndexHistory>> groupedByIndex = rawHistories.stream()
                .collect(Collectors.groupingBy(h -> IndexType.fromSeriesId(h.getSymbol())));

        // 5. Entity를 MarketChartData(DTO)로 변환하여 최종 Map 구성
        return groupedByIndex.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> MarketChartData.from(entry.getValue())
                ));
    }
}