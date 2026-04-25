package com.stocklog.domain.stock.dto.vo.info;

import com.stocklog.domain.stock.type.MarketType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;


@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class StockInfo {
    private String code;            // 종목 코드
    private String name;            // 종목명
    private String sector;          // 표준산업분류(섹터)
    private MarketType marketType;  // 시장 구분
    private BigDecimal totalShares; // 상장주수 (FMP 보정 트리거)

    public static StockInfo createFallback() {
        return StockInfo.builder()
                .code("")
                .name("")
                .sector("")
                .marketType(MarketType.UNKNOWN)
                .totalShares(BigDecimal.ZERO)
                .build();
    }
}