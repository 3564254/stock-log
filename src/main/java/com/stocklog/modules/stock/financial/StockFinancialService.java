package com.stocklog.modules.stock.financial;

import com.stocklog.modules.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.modules.stock.financial.dto.response.StockFinancialsResponse;
import com.stocklog.modules.stock.stock.StockService;
import com.stocklog.modules.stock.stock.dto.StockDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
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
        StockDto stock = stockService.getStockDto(code);
        LocalDate today;
        if (stock.market().isDomestic()) {
            today = LocalDate.now();
        } else {
            today = LocalDate.now(ZoneId.of("America/New_York"));
        }

        return financialRepository.findFirstByCodeOrderByBaseDateDesc(code)
                .filter(s -> s.getBaseDate() != null && s.getBaseDate().equals(today))
                .map(s -> {
                    log.info("[Financial] DB 캐시 데이터 반환 (최신) - 종목코드: {}", code);
                    return StockFinancialDto.from(s);
                })
                .orElseGet(() -> {
                    log.info("[Financial] DB 데이터 없음 또는 갱신 필요. 외부 API 연동 시작 - 종목코드: {}", code);
                    return fetchAndSaveFromExternal(code);
                });
    }

    private StockFinancialDto fetchAndSaveFromExternal(String code) {
        StockDto stockDto = stockService.getStockDto(code);

        StockFinancialsResponse response = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .findFirst()
                .map(p -> {
                    return p.fetchFinancial(code);
                })
                .orElseThrow(() -> {
                    log.error("[Financial] 적절한 Provider를 찾을 수 없습니다. (마켓: {}, 코드: {})", stockDto.market(), code);
                    return new IllegalArgumentException("지원하지 않는 시장이거나 종목이 없습니다: " + code);
                });

        StockFinancialDto dto = response.stockFinancialDto();

        financialRepository.save(StockFinancialDto.toEntity(dto, code));
        return dto;
    }
}