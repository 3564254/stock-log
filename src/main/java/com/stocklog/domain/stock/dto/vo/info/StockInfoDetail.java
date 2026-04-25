package com.stocklog.domain.stock.dto.vo.info;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;


@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StockInfoDetail extends StockInfo {

    // [1. 지수 업종 분류 - 사용자 요청 사항]
    private String indexLarge;      // 지수대분류명
    private String indexMedium;     // 지수중분류명
    private String indexSmall;      // 지수소분류명

    // [2. 자본 및 리스크 관리]
    private BigDecimal parValue;    // 액면가
    private String isTradeStop;     // 거래정지여부 (Y/N)
    private String isAdminItem;     // 관리종목여부 (Y/N)

    public static StockInfoDetail createDetailFallback() {
        return StockInfoDetail.builder()
                .code("000000")
                .name("Unknown")
                .sector("Unknown")
                .totalShares(BigDecimal.ZERO)
                .indexLarge("-")
                .indexMedium("-")
                .indexSmall("-")
                .isTradeStop("N")
                .isAdminItem("N")
                .parValue(BigDecimal.ZERO)
                .build();
    }
}