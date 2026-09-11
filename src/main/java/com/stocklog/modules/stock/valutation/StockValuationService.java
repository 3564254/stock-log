package com.stocklog.modules.stock.valutation;

import com.stocklog.modules.stock.application.request.StockRequest;
import com.stocklog.modules.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.modules.stock.price.StockPriceService;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.stock.stock.StockService;
import com.stocklog.modules.stock.stock.dto.StockDto;
import com.stocklog.modules.stock.valutation.dto.StockValuationDto;
import com.stocklog.modules.stock.valutation.dto.ValuationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockValuationService {

    private final StockValuationRepository stockValuationRepository;
    private final List<StockValuationProvider> providers;
    private final StockPriceService stockPriceService;
    private final StockService stockService;

    @Transactional
    public StockValuationDto fetchValuation(String code) {
        StockDto stock = stockService.getStockDto(code);
        LocalDate today;
        if (stock.market().isDomestic()) {
            today = LocalDate.now();
        } else {
            today = LocalDate.now(ZoneId.of("America/New_York"));
        }

        return stockValuationRepository.findFirstByCodeOrderByBaseDateDesc(code)
                .filter(s -> s.getBaseDate() != null && s.getBaseDate().equals(today))
                .map(s ->{
                    log.info("[Valuation] DB 캐시 데이터 반환 (최신) - 종목코드: {}", code);
                    return StockValuationDto.from(s);
                })
                .orElseGet( () -> {
                            log.info("[Valuation] DB 데이터 없음 또는 갱신 필요. 외부 API 연동 시작 - 종목코드: {}", code);
                            return fetchAndSaveFromExternal(code);
                        }
                );
    }

    @Transactional
    public StockValuationDto  updatePeg(String code ,BigDecimal peg ,StockValuationDto valuation) {
        StockValuationDto valuationDto = StockValuationDto.builder()
                .baseDate(valuation.baseDate())
                .per(valuation.per())
                .pbr(valuation.pbr())
                .peg(peg)
                .eps(valuation.eps())
                .fper(valuation.fper())
                .feps(valuation.feps())
                .targetPrice(valuation.targetPrice())
                .build();
        stockValuationRepository.save(StockValuationDto.toEntity(valuationDto, code));
        return valuationDto;
    }

    private StockValuationDto fetchAndSaveFromExternal(String code) {
        StockDto stockDto = stockService.getStockDto(code);
        StockPriceDto stockPriceDto = stockPriceService.fetchPrice(code);

        // 전략 패턴 탐색 과정 로그
        ValuationResponse response = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .findFirst()
                .map(p -> {
                    return p.fetchValuation(StockRequest.from(stockDto));
                })
                .orElseThrow(() -> {
                    log.error("[Valuation] 지원하지 않는 마켓 타입: {}, 코드: {}", stockDto.market(), code);
                    return new IllegalArgumentException("해당 마켓의 Valuation을 지원하는 Provider가 없습니다. 코드: " + code);
                });

        StockValuationDto dto = StockValuationDto.builder()
                .baseDate(LocalDate.now())
                .per(stockPriceDto.per())
                .pbr(stockPriceDto.pbr())
                .peg(response.getPeg())
                .eps(stockPriceDto.eps())
                .feps(response.getFeps())
                .fper(response.getFper())
                .targetPrice(response.getTargetPrice())
                .build();

        stockValuationRepository.save(StockValuationDto.toEntity(dto, code));

        return dto;
    }

    @Transactional
    public StockValuationDto ensurePegCalculated(String code, StockFinancialDto financialDto, StockValuationDto valuationDto) {
        if (valuationDto.peg().compareTo(BigDecimal.ZERO) == 0 && valuationDto.per() != null && financialDto.epsGrowth() != null) {
            if (financialDto.epsGrowth().compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal peg = valuationDto.per().divide(financialDto.epsGrowth(), 2, RoundingMode.HALF_UP);
                valuationDto = updatePeg(code, peg, valuationDto);
            } else {
                log.warn("[Stock] EPS Growth가 0이어서 PEG를 계산할 수 없습니다. 종목코드: {}", code);
            }
        }
        return valuationDto;
    }
}