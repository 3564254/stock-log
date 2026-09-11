package com.stocklog.modules.purchase.trade.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.stocklog.modules.purchase.trade.dto.vo.TradeLogItem;
import com.stocklog.modules.purchase.trade.entity.TradeLog;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;


@Builder
public record TradeLogResponse(
        BigDecimal totalRealizedProfit, // 총 실현 손익 (이번 달 총 얼마?)
        BigDecimal successRate, // 성공률 (%)

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate currentYearMonth,    // 조회 달

        Map<LocalDate, List<TradeLogItem>> dailyLogs
) {

    public static TradeLogResponse of(LocalDate currentYearMonth, List<TradeLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return TradeLogResponse.builder()
                    .totalRealizedProfit(BigDecimal.ZERO)
                    .successRate(BigDecimal.ZERO)
                    .currentYearMonth(currentYearMonth)
                    .dailyLogs(Collections.emptyMap())
                    .build();
        }

        Map<LocalDate, List<TradeLogItem>> dailyLogs = new LinkedHashMap<>();
        BigDecimal totalProfit = BigDecimal.ZERO;
        long successCount = 0;

        for (TradeLog log : logs) {
            TradeLogItem item = TradeLogItem.from(log);
            totalProfit = totalProfit.add(item.profit());

            if (item.isProfit()) successCount++;

            dailyLogs.computeIfAbsent(log.getTradeDate(), k -> new ArrayList<>())
                    .add(item);
        }

        // 성공률 계산: (수익 난 거래 수 / 전체 거래 수) * 100
        BigDecimal successRate = BigDecimal.valueOf(successCount)
                .divide(BigDecimal.valueOf(logs.size()), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return TradeLogResponse.builder()
                .totalRealizedProfit(totalProfit)
                .successRate(successRate)
                .currentYearMonth(currentYearMonth)
                .dailyLogs(dailyLogs)
                .build();
    }
}