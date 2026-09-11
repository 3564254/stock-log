package com.stocklog.modules.stock.valutation.dto;

import com.stocklog.modules.stock.valutation.StockValuation;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record StockValuationDto(
        LocalDate baseDate,
        BigDecimal per,
        BigDecimal pbr,

        BigDecimal peg,
        BigDecimal eps,
        BigDecimal fper,
        BigDecimal feps,
        BigDecimal targetPrice
){
    public static StockValuation toEntity(StockValuationDto valuationDto, String code) {
        return StockValuation.builder()
                .code(code)
                .baseDate(valuationDto.baseDate)
                .per(valuationDto.per)
                .pbr(valuationDto.pbr)
                .peg(valuationDto.peg)
                .eps(valuationDto.eps)
                .fper(valuationDto.fper)
                .feps(valuationDto.feps)
                .targetPrice(valuationDto.targetPrice)
                .build();
    }

    public static StockValuationDto from(StockValuation valuation) {
        return StockValuationDto.builder()
                .baseDate(valuation.getBaseDate())
                .per(valuation.getPer())
                .pbr(valuation.getPbr())
                .peg(valuation.getPeg())
                .eps(valuation.getEps())
                .fper(valuation.getFper())
                .feps(valuation.getFeps())
                .targetPrice(valuation.getTargetPrice())
                .build();
    }


}
