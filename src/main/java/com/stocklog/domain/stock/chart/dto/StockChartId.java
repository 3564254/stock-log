package com.stocklog.domain.stock.chart.dto;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDate;
import com.stocklog.domain.stock.common.type.Period;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StockChartId implements Serializable {
    private String code;      // StockChart의 필드명과 일치해야 함
    private LocalDate tradeDate;
    private Period period;
}
