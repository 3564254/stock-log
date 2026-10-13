package com.stocklog.modules.purchase.portfolio.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Builder
public record PortfolioResponse(
        List<HoldingResponse> myStocks,
        BigDecimal averageReturnRate,   // 종목 단순 평균 수익률
        BigDecimal totalMarketValueKrw, // 수량을 입력한 종목의 원화 환산 평가금액 합 (없으면 null)
        int weightedCount               // 비중 계산에 들어간 종목 수 (수량 입력 + 환율 확보)
) {
    public static PortfolioResponse of(List<HoldingResponse> holdings) {
        return of(holdings, null);
    }

    /**
     * @param usdKrwRate 달러 종목을 원화로 바꿀 환율. null이거나 0 이하면 달러 종목은 비중 계산에서 빠진다
     */
    public static PortfolioResponse of(List<HoldingResponse> holdings, BigDecimal usdKrwRate) {
        // 현재가를 못 가져온 종목(returnRate == null)은 평균에서 뺀다
        List<BigDecimal> rates = holdings.stream().map(HoldingResponse::returnRate).filter(Objects::nonNull).toList();
        BigDecimal avgRate = rates.isEmpty() ? BigDecimal.ZERO :
                rates.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(rates.size()), 4, RoundingMode.HALF_UP);

        List<BigDecimal> krwValues = holdings.stream().map(h -> toKrw(h, usdKrwRate)).toList();
        BigDecimal total = krwValues.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasTotal = total.signum() > 0;

        List<HoldingResponse> withWeights = IntStream.range(0, holdings.size())
                .mapToObj(i -> {
                    BigDecimal krw = krwValues.get(i);
                    BigDecimal weight = (hasTotal && krw != null)
                            ? krw.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP)
                            : null;
                    return holdings.get(i).withWeight(weight);
                })
                .toList();

        return PortfolioResponse.builder()
                .myStocks(withWeights)
                .averageReturnRate(avgRate)
                .totalMarketValueKrw(hasTotal ? total.setScale(0, RoundingMode.HALF_UP) : null)
                .weightedCount(hasTotal ? (int) krwValues.stream().filter(Objects::nonNull).count() : 0)
                .build();
    }

    public static PortfolioResponse createFallback() {
        return PortfolioResponse.builder()
                .myStocks(List.of())
                .averageReturnRate(BigDecimal.ZERO)
                .totalMarketValueKrw(null)
                .weightedCount(0)
                .build();
    }

    private static BigDecimal toKrw(HoldingResponse h, BigDecimal usdKrwRate) {
        if (h.marketValue() == null) return null;
        if (h.domestic()) return h.marketValue();
        if (usdKrwRate == null || usdKrwRate.signum() <= 0) return null;
        return h.marketValue().multiply(usdKrwRate);
    }
}
