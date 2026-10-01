package com.stocklog.modules.market.analysis.service.analyzer.utils;

import com.stocklog.modules.market.chart.MarketChartData;
import com.stocklog.modules.market.domain.type.IndexType;
import com.stocklog.modules.market.index.dto.IndexChartDto;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public final class GetDoublesUtils {

    private static final int DEFAULT_REQUIRED_SIZE_DAILY = 20;
    private static final int DEFAULT_REQUIRED_SIZE_WEEK = 10;
    private static final int DEFAULT_REQUIRED_SIZE_MONTH = 5;

    private GetDoublesUtils() {
    }

    // 1. 기본 요구 개수(20) 적용 오버로딩
    public static List<Double> getDoubles(Map<IndexType, MarketChartData> data, IndexType indexType) {
        return getDoubles(data, indexType, getRuquiredSize(indexType));
    }

    // 2. Map에서 데이터를 꺼내 위임
    public static List<Double> getDoubles(Map<IndexType, MarketChartData> data, IndexType indexType, int required) {
        if (data == null || !data.containsKey(indexType)) {
            log.warn("[{}] 해당 인덱스의 차트 데이터가 존재하지 않습니다.", indexType != null ? indexType.getSeriesId() : "UNKNOWN");
            return Collections.emptyList();
        }
        return getDoubles(data.get(indexType), indexType, required);
    }

    // 3. MarketChartData에서 DTO 리스트 추출 후 위임
    public static List<Double> getDoubles(MarketChartData data, IndexType indexType, int required) {
        if (data == null || data.chartDataList() == null) {
            log.warn("[{}] 차트 데이터 목록이 null입니다.", indexType.getSeriesId());
            return Collections.emptyList();
        }
        return getDoubles(data.chartDataList(), indexType, required);
    }

    public static List<Double> getDoubles(MarketChartData data, IndexType indexType) {
        return getDoubles(data, indexType,  getRuquiredSize(indexType));
    }

    public static List<Double> getDoubles(List<IndexChartDto> indexChartDtos, IndexType indexType) {
        return getDoubles(indexChartDtos, indexType,  getRuquiredSize(indexType));
    }

    // 4. 최종 변환 및 검증이 집중되는 단일 메서드
    public static List<Double> getDoubles(List<IndexChartDto> indexChartDtos, IndexType indexType, int required) {
        if (indexChartDtos == null || indexChartDtos.isEmpty()) {
            log.warn("[{}] 차트 정보가 비어있습니다.", indexType.getSeriesId());
            return Collections.emptyList();
        }

        if (indexChartDtos.size() < required) {
            log.info("[{}] 차트 정보가 부족합니다 [{}/{}]", indexType.getSeriesId(), indexChartDtos.size(), required);
        }

        return indexChartDtos.stream()
                .filter(dto -> dto != null && dto.close() != null)
                .map(dto -> dto.close().doubleValue())
                .toList();
    }

    private static int getRuquiredSize(IndexType type) {
        return switch (type.getFrequency()) {
            case DAY -> DEFAULT_REQUIRED_SIZE_DAILY;
            case WEEK -> DEFAULT_REQUIRED_SIZE_WEEK;
            case MONTH -> DEFAULT_REQUIRED_SIZE_MONTH;
            default -> 0;
        };
    }
}