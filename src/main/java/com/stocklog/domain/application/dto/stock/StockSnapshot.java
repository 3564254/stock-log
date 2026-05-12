package com.stocklog.domain.application.dto.stock;

import com.stocklog.domain.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.domain.stock.info.dto.internal.StockInfoDto;
import com.stocklog.domain.stock.chart.dto.internal.StockChartDto;
import com.stocklog.domain.stock.price.dto.internal.StockPriceDto;
import com.stocklog.domain.stock.valutation.dto.internal.StockValuationDto;
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