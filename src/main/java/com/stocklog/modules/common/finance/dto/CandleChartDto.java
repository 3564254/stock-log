package com.stocklog.modules.common.finance.dto;

import java.math.BigDecimal;

public interface CandleChartDto extends SimpleChartDto {
    BigDecimal open();
    BigDecimal high();
    BigDecimal low();
    BigDecimal volume();
}
