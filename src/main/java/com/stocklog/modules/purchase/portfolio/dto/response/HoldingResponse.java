package com.stocklog.modules.purchase.portfolio.dto.response;

import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.purchase.portfolio.entity.Holding;
import com.stocklog.modules.stock.info.StockInfo;
import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
public record HoldingResponse(
        Long holdingId,
        String stockCode,
        String korName,
        String sector,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        BigDecimal highestPriceSinceBuy,
        BigDecimal returnRate,  // 수익률(%)
        BigDecimal quantity,    // 보유 수량 (선택 입력, 없으면 null)
        boolean domestic,       // true: 원화 종목, false: 달러 종목
        BigDecimal marketValue, // 평가금액 = 수량 × 현재가 (종목 통화 기준, 수량 없으면 null)
        BigDecimal weight       // 원화 환산 평가금액 기준 비중(%). 계산할 수 없으면 null
) {
    public static HoldingResponse of(Holding holding, BigDecimal currentPrice) {
        return HoldingResponse.builder()
                .holdingId(holding.getId())
                .stockCode(holding.getStock().getCode())
                .korName(holding.getStock().getName())
                .sector(holding.getStock().getSector())
                .averagePrice(holding.getAveragePrice())
                .currentPrice(currentPrice)
                .highestPriceSinceBuy(holding.getHighestPriceSinceBuy())
                .returnRate(holding.calculateReturnRate(currentPrice))
                .quantity(holding.getQuantity())
                .domestic(isDomestic(holding.getStock()))
                .marketValue(holding.calculateMarketValue(currentPrice))
                .build();
    }

    /** 비중은 전체 합계를 알아야 하므로 PortfolioResponse에서 채운다 */
    public HoldingResponse withWeight(BigDecimal weight) {
        return toBuilder().weight(weight).build();
    }

    // 국가 정보가 비어 있으면 숫자 코드(국내 종목 코드 형식)로 판단한다. 화면의 isKrCode와 같은 기준
    private static boolean isDomestic(StockInfo stock) {
        if (stock.getNation() != null && stock.getNation() != Nation.UNKNOWN) {
            return stock.getNation().isDomestic();
        }
        return stock.getCode() != null && stock.getCode().matches("\\d+");
    }
}
