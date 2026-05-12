package com.stocklog.domain.stock.valutation;

import com.stocklog.domain.stock.common.StockRequest;
import com.stocklog.domain.stock.price.StockPriceService;
import com.stocklog.domain.stock.price.dto.internal.StockPriceDto;
import com.stocklog.domain.stock.stock.StockService;
import com.stocklog.domain.stock.stock.dto.StockDto;
import com.stocklog.domain.stock.valutation.dto.internal.StockValuationDto;
import com.stocklog.domain.stock.valutation.dto.response.ValuationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        log.info("[Valuation] 주식 가치 평가 데이터 조회 요청 - 종목코드: {}", code);

        return stockValuationRepository.findById(code)
                .map(entity -> {
                    log.info("[Valuation] DB 캐시 데이터 반환 - 종목코드: {}", code);
                    return StockValuationDto.from(entity);
                })
                .orElseGet(() -> {
                    log.info("[Valuation] DB 데이터 없음. 외부 API 연동 시작 - 종목코드: {}", code);
                    return fetchAndSaveFromExternal(code);
                });
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
        StockDto stockDto = stockService.getStock(code);
        StockPriceDto stockPriceDto = stockPriceService.fetchPrice(code);

        // 전략 패턴 탐색 과정 로그
        ValuationResponse response = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .findFirst()
                .map(p -> {
                    log.info("[Valuation] Provider 선정 완료: {} (마켓: {})", p.getClass().getSimpleName(), stockDto.market());
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
        log.info("[Valuation] 외부 데이터 저장 및 결과 반환 완료 - 종목코드: {}, 목표가: {}", code, response.getTargetPrice());

        return dto;
    }
}