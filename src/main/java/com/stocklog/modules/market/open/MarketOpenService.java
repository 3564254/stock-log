package com.stocklog.modules.market.open;

import com.stocklog.modules.common.type.Nation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 장 운영 상태. 외부 API(토스)가 실패해도 호출한 화면이 깨지지 않도록
 * "정보 없음"(CLOSED, 시각 없음)으로 돌려준다. 화면 칩은 이 경우 '장 정보 없음'으로 보인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketOpenService {
    private final MarketOpenProvider marketOpenProvider;

    public MarketOpenResponse getMarketOpenResponse() {
        return MarketOpenResponse.builder()
                .krMarketOpen(getMarketOpenDto(Nation.KR))
                .usMarketOpen(getMarketOpenDto(Nation.US))
                .build();
    }

    public MarketOpenInfo getMarketOpenDto(Nation nation) {
        try {
            return marketOpenProvider.fetchMarketOpenInfo(nation);
        } catch (Exception e) {
            log.warn("[MarketOpen] {} 장 운영 정보 조회 실패 → 정보 없음으로 표시: {}", nation, e.getMessage());
            return new MarketOpenInfo(nation, MarketOpenType.CLOSED, null, null, false);
        }
    }







}
