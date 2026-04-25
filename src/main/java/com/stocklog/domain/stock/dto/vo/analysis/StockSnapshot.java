package com.stocklog.domain.stock.dto.vo.analysis;

import com.stocklog.domain.stock.type.Currency;
import com.stocklog.domain.stock.type.MarketType;
import com.stocklog.domain.stock.type.Nation;
import lombok.*;

import java.math.BigDecimal;


@Value
@Builder
@Getter
public class StockSnapshot {
    String code; String name;
    Nation nation; MarketType market; Currency currency;
    String sector;
    String indexLarge; String indexMedium; String indexSmall;

    // 밸류에이션
    BigDecimal per; BigDecimal pbr;
    BigDecimal eps; BigDecimal bps; BigDecimal parValue;

    BigDecimal currentPrice;
    BigDecimal marketCap; BigDecimal volume;
    BigDecimal w52High; BigDecimal w52Low;

    String isTradeStop; String isAdminItem;

}

