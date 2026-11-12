package com.stocklog.modules.stock.screening;

import com.stocklog.modules.common.type.Nation;

import java.util.List;

/** 시가총액 순 종목 목록. 스크리닝 후보를 고르는 데 쓴다 */
public interface MarketCapProvider {

    /** 국가별 종목을 시가총액 내림차순으로. 실패하면 빈 목록 */
    List<MarketCapStock> fetchByMarketCap(Nation nation);
}
