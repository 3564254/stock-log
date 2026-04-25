package com.stocklog.domain.stock.dto.vo.price;
import com.stocklog.domain.stock.type.Period;
import lombok.*;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StockChartData {
    private String time;
    private BigDecimal value;
    private Period timeFrame;

    private BigDecimal open;       // 시가
    private BigDecimal high;       // 고가
    private BigDecimal low;        // 저가
    private BigDecimal volume;     // 거래량

    public StockChartData(String time, BigDecimal value, Period timeFrame) {
        this.time = time;
        this.value = value;
        this.timeFrame = timeFrame;
    }


    public static StockChartData createFallback() {
        return StockChartData.builder()
                .time("yyyy-mm-dd")
                .value(BigDecimal.ZERO)
                .build();
    }

    public static String formatDate(String rawDate) {
        return rawDate.substring(0, 4) + "-" +
                rawDate.substring(4, 6) + "-" +
                rawDate.substring(6, 8);
    }

}
