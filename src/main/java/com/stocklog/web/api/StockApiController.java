package com.stocklog.web.api;

import com.stocklog.modules.stock.analysis.StockAnalysisService;
import com.stocklog.modules.stock.analysis.MarketRegimeService;
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
import com.stocklog.modules.stock.briefing.CompanyBriefing;
import com.stocklog.modules.stock.briefing.CompanyBriefingService;
import com.stocklog.modules.ai.AiException;
import com.stocklog.modules.stock.report.StockAiReportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@Slf4j
public class StockApiController {

    // TODO: 의존 서비스가 많아 Facade로 묶기

    private final StockPriceService priceService;
    private final StockInfoService infoService;
    private final StockFinancialService financialService;
    private final StockAnalysisService analysisService;

    private final StockChartService chartService;
    private final com.stocklog.modules.stock.signal.Rsi2Service rsi2Service;
    private final MarketRegimeService marketRegimeService;

    private final StockQuoteUseCase quoteUseCase;
    private final StockSnapshotUseCase snapshotUseCase;
    private final StockSummaryUseCase summaryUseCase;

    private final GeminiAiProvider geminiAiProvider;
    private final CompanyBriefingService briefingService;
    private final StockAiReportService aiReportService;
    private final com.stocklog.modules.stock.logo.StockLogoService logoService;

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

    /** 종목 로고 URL 묶음 (미국 종목만). 아직 못 받은 종목은 pending */
    @GetMapping("/logos")
    public com.stocklog.modules.stock.logo.StockLogoService.Logos getLogos(@RequestParam List<String> codes) {
        return logoService.logos(codes);
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

    /** 종목이 속한 시장이 40주선 아래인지. 분석 화면의 시장 상태 표시용 */
    @GetMapping("/{code}/market-regime")
    public MarketRegimeService.MarketRegime getMarketRegime(@PathVariable String code) {
        return marketRegimeService.forStock(code);
    }

    /** RSI2 눌림 (참고 신호, DB 봉만). 데이터가 부족하면 204 */
    @GetMapping("/{code}/rsi2")
    public ResponseEntity<?> getRsi2(@PathVariable String code) {
        var r = rsi2Service.of(code);
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/{code}/chart")
    public StockChartResponse getStockChart(
            @PathVariable String code,
            @RequestParam(defaultValue = "DAY") Period period) {
        return chartService.getChartListDto(code, period);
    }

    /** 분석 화면 '회사 소식': 다음 실적일 · 최근 실적 · 최근 뉴스 제목 (미국 종목만, 표시용) */
    @GetMapping("/{code}/briefing")
    public CompanyBriefing getBriefing(@PathVariable String code) {
        return briefingService.get(code);
    }

    /** 회사 소식 AI 뉴스 요약 */
    @PostMapping("/{code}/news-summary")
    public ResponseEntity<?> summarizeNews(@PathVariable String code) {
        try {
            return ResponseEntity.ok(briefingService.summarize(code));
        } catch (CompanyBriefingService.SummaryUnavailableException e) {
            // QUOTA(AI 사용량 소진)는 429로 따로
            int status = switch (e.code()) { case "QUOTA" -> 429; case "ERROR" -> 502; default -> 422; };
            return ResponseEntity.status(status).body(Map.of("code", e.code(), "message", e.getMessage()));
        }
    }

    /** 마지막 AI 리포트 (오늘 것이 아니어도). 없으면 204 */
    @GetMapping("/{code}/ai-report")
    public ResponseEntity<StockAiReportService.View> getLatestAiReport(@PathVariable String code) {
        return aiReportService.latest(code).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    /** AI 리포트 새로 받기 */
    @PostMapping("/{code}/ai-report")
    public ResponseEntity<?> createAiReport(@PathVariable String code) {
        try {
            StockSnapshot stockSnapshot = snapshotUseCase.getStockSnapshot(code);
            StockAnalysisResult analysisResult = analysisService.getAnalysisResult(stockSnapshot);
            return ResponseEntity.ok(aiReportService.generate(analysisResult));
        } catch (AiException e) {
            return ResponseEntity.status(e.isQuota() ? 429 : 502).body(Map.of("code", e.code(), "message", e.userMessage()));
        }
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

    // TODO: StockFacadeService.getStockAiComment(code) 로 위임 대상
    @GetMapping("/{code}/ai-comment")
    public ResponseEntity<String> getAiComment(@PathVariable String code) {
        StockSnapshot stockSnapshot = snapshotUseCase.getStockSnapshot(code);
        StockAnalysisResult analysisResult = analysisService.getAnalysisResult(stockSnapshot);
        String comment = geminiAiProvider.getStockAnalysis(analysisResult);
        return ResponseEntity.ok(comment);
    }
}