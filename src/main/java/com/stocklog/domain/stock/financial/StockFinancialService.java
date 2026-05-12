package com.stocklog.domain.stock.financial;

import com.stocklog.domain.stock.common.StockMasterDto;
import com.stocklog.domain.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.domain.stock.financial.dto.response.StockFinancialsResponse;
import com.stocklog.domain.stock.stock.StockService;
import com.stocklog.domain.stock.stock.dto.StockDto;
import com.stocklog.global.common.StockCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockFinancialService {
    private final StockFinancialRepository financialRepository;
    private final List<StockFinanceProvider> providers;
    private final StockService stockService;

    @Transactional
    public StockFinancialDto fetchFinancial(String code) {
        log.info("[Financial] 재무 정보 조회 시작 - 종목코드: {}", code);

        return financialRepository.findById(code)
                .map(entity -> {
                    log.info("[Financial] DB 캐시 데이터 발견 - 종목코드: {}", code);
                    return StockFinancialDto.from(entity);
                })
                .orElseGet(() -> {
                    log.info("[Financial] DB 데이터 없음. 외부 API 연동을 시작합니다 - 종목코드: {}", code);
                    return fetchAndSaveFromExternal(code);
                });
    }

    private StockFinancialDto fetchAndSaveFromExternal(String code) {
        StockDto stockDto = stockService.getStock(code);

        // 전략 패턴 탐색 및 실행 로그
        StockFinancialsResponse response = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .findFirst()
                .map(p -> {
                    log.info("[Financial] 선택된 Provider: {} (마켓: {})", p.getClass().getSimpleName(), stockDto.market());
                    return p.fetchFinancial(code);
                })
                .orElseThrow(() -> {
                    log.error("[Financial] 적절한 Provider를 찾을 수 없습니다. (마켓: {}, 코드: {})", stockDto.market(), code);
                    return new IllegalArgumentException("지원하지 않는 시장이거나 종목이 없습니다: " + code);
                });

        StockFinancialDto dto = response.stockFinancialDto();

        // 저장 로그
        financialRepository.save(StockFinancialDto.toEntity(dto, code));
        log.info("[Financial] 외부 재무 데이터 저장 완료 - 종목코드: {}", code);

        return dto;
    }
}