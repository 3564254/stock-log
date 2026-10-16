package com.stocklog.modules.market.ranking;

import com.stocklog.modules.common.type.Nation;

public interface MarketRankingProvider {
    /** 국가별 순위. 실패하면 rankedAt = null, rankings = 빈 목록 */
    MarketRankingInfo fetchMarketRankings(Nation nation);
}
