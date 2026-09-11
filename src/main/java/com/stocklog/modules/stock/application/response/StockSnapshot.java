package com.stocklog.modules.stock.application.response;

import com.stocklog.modules.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.modules.stock.info.dto.StockInfoDto;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.valutation.dto.StockValuationDto;
import lombok.Builder;

import java.util.List;

@Builder
public record StockSnapshot(
        StockInfoDto info,
        StockPriceDto price,
        StockFinancialDto financial,
        StockValuationDto valuation,
        List<StockChartDto> dailyCharts,
        List<StockChartDto> weeklyCharts
) {
}