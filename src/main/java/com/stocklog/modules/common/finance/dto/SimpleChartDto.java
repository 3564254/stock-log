package com.stocklog.modules.common.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SimpleChartDto {
    LocalDate date();
    BigDecimal close();
}
