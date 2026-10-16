package com.stocklog.infra.toss.market.ranking;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 토스 랭킹 응답 result — rankings는 순위 목록(배열)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossMarketRankingOutput(
        OffsetDateTime rankedAt,
        List<Ranking> rankings
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ranking(
            Long rank,
            String symbol,
            String currency,
            Price price,
            String tradingVolume,
            String tradingAmount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(
            String lastPrice,
            String basePrice,
            String changeRate
    ) {
    }
}
