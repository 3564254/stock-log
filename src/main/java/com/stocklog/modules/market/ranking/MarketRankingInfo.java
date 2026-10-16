package com.stocklog.modules.market.ranking;

import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.domain.type.Currency;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 거래대금 순위. rankedAt == null 이면 순위를 받지 못한 상태
 */
@Builder
public record MarketRankingInfo(
        Nation nation,                  // 어느 시장 순위인지 (화면 라벨용)
        LocalDateTime rankedAt,         // KST
        boolean realtime,               // true: 장중 실시간 순위 / false: 직전 거래일(1d) 순위
        List<MarketRanking> rankings
) {
    @Builder
    public record MarketRanking(
            Long rank,
            String code,
            String name,                // 종목명 — 토스 응답엔 없어서 서비스에서 stock 테이블로 채운다
            Currency currency,
            BigDecimal lastPrice,
            BigDecimal basePrice,
            BigDecimal changeRate,
            BigDecimal tradingVolume,
            BigDecimal tradingAmount
    ) {
        public MarketRanking withName(String name) {
            return new MarketRanking(rank, code, name, currency, lastPrice, basePrice, changeRate, tradingVolume, tradingAmount);
        }
    }
}
