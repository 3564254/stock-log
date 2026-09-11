package com.stocklog.modules.stock.chart.id;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDate;
import com.stocklog.modules.stock.domain.type.Period;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StockChartId implements Serializable {
    private String code;
    private LocalDate tradeDate;
    private Period period;
}
