package com.stocklog.web.api;

import com.stocklog.modules.stock.analysis.StockAnalysisService;
import com.stocklog.modules.stock.application.response.StockQuote;
import com.stocklog.modules.stock.application.response.StockSnapshot;
import com.stocklog.modules.stock.application.usecase.StockQuoteUseCase;
import com.stocklog.modules.stock.application.usecase.StockSnapshotUseCase;
import com.stocklog.modules.stock.application.usecase.StockSummaryUseCase;
import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.chart.dto.StockChartResponse;
import com.stocklog.modules.stock.analysis.result.StockAnalysisResult;
import com.stocklog.modules.stock.financial.StockFinancialService;
import com.stocklog.modules.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.modules.stock.info.StockInfoService;
import com.stocklog.modules.stock.info.dto.StockInfoDto;
import com.stocklog.modules.stock.price.StockPriceService;
import com.stocklog.modules.stock.price.dto.StockPriceDto;
import com.stocklog.modules.common.type.Period;
import com.stocklog.infra.gemini.service.GeminiAiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@Slf4j
public class StockApiController {

    // TODO(아키텍처/리팩토링):
    // 1. 현재 Controller가 저수준 서비스/쿼리 8개를 직접 주입받아 결합도가 너무 높음.
    // 2. getAnalysis, getAiComment처럼 '스냅샷 조회 -> 분석 엔진 -> AI 코멘트'로 이어지는
    //    오케스트레이션(비즈니스 조립) 로직이 Controller에 노출되어 있음.
    // 3. StockFacadeService(또는 StockApplicationService)를 하나 두고,
    //    - snapshotQuery + analysisService + geminiAiProvider를 묶는 복합 흐름은 Facade로 위임할 것.
    //    - 나머지 단일 조회(단순 CRUD/Query)는 지금처럼 직접 호출하거나 엔드포인트 분리 검토.

    private final StockPriceService priceService;
    private final StockInfoService infoService;
    private final StockFinancialService financialService;
    private final StockAnalysisService analysisService;

    private final StockChartService chartService;

    private final StockQuoteUseCase quoteUseCase;
    private final StockSnapshotUseCase snapshotUseCase;
    private final StockSummaryUseCase summaryUseCase;

    private final GeminiAiProvider geminiAiProvider;


    @GetMapping("/search")
    public List<StockQuote> searchStocks(
            @RequestParam String name,
            @PageableDefault(size = 5) Pageable pageable) {
        return quoteUseCase.getStockQuoteListByKeyword(name, pageable);
    }

    @GetMapping("/{code}")
    public StockPriceDto getPrice(@PathVariable String code) {
        return priceService.fetchPrice(code);
    }

    @GetMapping("/prices")
    public Map<String, StockPriceDto> getPricesInBulk(@RequestParam List<String> codes) {
        return priceService.fetchPricesInBulk(codes);
    }

    @GetMapping("/{code}/info")
    public StockInfoDto getInfo(@PathVariable String code) {
        return infoService.fetchInfoDto(code);
    }

    @GetMapping("/{code}/detail")
    public StockSnapshot getStockDetail(@PathVariable String code) {
        return snapshotUseCase.getStockSnapshot(code);
    }

    @GetMapping("/{code}/chart")
    public StockChartResponse getStockChart(
            @PathVariable String code,
            @RequestParam(defaultValue = "DAY") Period period) {
        return chartService.getChartListDto(code, period);
    }

    @GetMapping("/{code}/financial")
    public StockFinancialDto getFinancialsResponse(@PathVariable String code) {
        return financialService.fetchFinancial(code);
    }

    // TODO: StockFacadeService.getStockAnalysis(code) 로 위임 대상
    @GetMapping("/{code}/analysis")
    public StockAnalysisResult getAnalysis(@PathVariable String code) {
        StockSnapshot stockSnapshot = snapshotUseCase.getStockSnapshot(code);
        return analysisService.getAnalysisResult(stockSnapshot);
    }

    // TODO: StockFacadeService.getStockAiComment(code) 로 위임 대상 (스냅샷 + 분석 + AI 3단계 조합)
    @GetMapping("/{code}/ai-comment")
    public ResponseEntity<String> getAiComment(@PathVariable String code) {
        StockSnapshot stockSnapshot = snapshotUseCase.getStockSnapshot(code);
        StockAnalysisResult analysisResult = analysisService.getAnalysisResult(stockSnapshot);
        String comment = geminiAiProvider.getStockAnalysis(analysisResult);
        return ResponseEntity.ok(comment);
    }
}