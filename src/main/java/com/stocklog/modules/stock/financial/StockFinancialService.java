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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 종목 재무 지표. 하루 한 번 외부에서 받아 DB에 저장한다 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockFinancialService {

    /** 이 날짜 이전 행은 예전 실패 대체값(LocalDate.MIN)이라 쓰지 않는다 */
    private static final LocalDate VALID_AFTER = LocalDate.of(2000, 1, 1);

    private final StockFinancialRepository financialRepository;
    private final List<StockFinanceProvider> providers;
    private final StockService stockService;

    /** 오늘 이미 실패한 종목 → 같은 날 다시 외부를 부르지 않는다 */
    private final Map<String, LocalDate> failedOn = new ConcurrentHashMap<>();

    @Transactional
    public StockFinancialDto fetchFinancial(String code) {
        StockDto stock = stockService.getStockDto(code);
        LocalDate today = stock.market().isDomestic() ? LocalDate.now(ZoneId.of("Asia/Seoul")) : LocalDate.now(ZoneId.of("America/New_York"));

        Optional<StockFinancial> last = financialRepository.findFirstByCodeAndBaseDateAfterOrderByBaseDateDesc(code, VALID_AFTER);
        if (last.isPresent() && today.equals(last.get().getBaseDate())) {
            return StockFinancialDto.from(last.get());
        }
        if (today.equals(failedOn.get(code))) {
            return last.map(StockFinancialDto::from).orElseGet(StockFinancialDto::createFallback);
        }

        StockFinancialDto fresh = null;
        try {
            fresh = fetchFromExternal(stock, code);
        } catch (Exception e) {
            log.warn("[Financial] 외부 재무 조회 실패 - 종목코드: {}, {}", code, e.getMessage());
        }
        if (fresh == null || fresh.isEmpty()) {
            failedOn.put(code, today);
            log.info("[Financial] 새 값 없음 → 마지막 정상 값 사용 ({}) - 종목코드: {}",
                    last.map(s -> String.valueOf(s.getBaseDate())).orElse("없음"), code);
            return last.map(StockFinancialDto::from).orElseGet(StockFinancialDto::createFallback);
        }
        StockFinancialDto toSave = StockFinancialDto.builder()
                .baseDate(today)
                .grossMargin(fresh.grossMargin()).operatingMargin(fresh.operatingMargin()).netMargin(fresh.netMargin())
                .roe(fresh.roe()).roa(fresh.roa())
                .revenueGrowth(fresh.revenueGrowth()).epsGrowth(fresh.epsGrowth())
                .dividendYield(fresh.dividendYield()).payoutRatio(fresh.payoutRatio())
                .currentRatio(fresh.currentRatio()).debtToEquity(fresh.debtToEquity()).inventoryTurnover(fresh.inventoryTurnover())
                .source(fresh.source())
                .build();
        financialRepository.save(StockFinancialDto.toEntity(toSave, code));
        failedOn.remove(code);
        return toSave;
    }

    private StockFinancialDto fetchFromExternal(StockDto stockDto, String code) {
        StockFinancialsResponse response = providers.stream()
                .filter(p -> p.supports(stockDto.market()))
                .findFirst()
                .map(p -> p.fetchFinancial(code))
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 시장이거나 종목이 없습니다: " + code));
        return response == null ? null : response.stockFinancialDto();
    }
}
