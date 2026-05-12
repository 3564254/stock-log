package com.stocklog.web.api;

import com.stocklog.domain.stock.analysis.StockAnalysisService;
import com.stocklog.domain.application.dto.stock.StockDashBoard;
import com.stocklog.domain.application.StockFacadeService;
import com.stocklog.domain.application.dto.stock.StockSnapshot;
import com.stocklog.domain.stock.chart.dto.internal.StockChartListDto;
import com.stocklog.domain.stock.analysis.result.StockAnalysisResult;
import com.stocklog.domain.stock.financial.dto.internal.StockFinancialDto;
import com.stocklog.domain.stock.info.dto.internal.StockInfoDto;
import com.stocklog.domain.stock.price.dto.internal.StockPriceDto;
import com.stocklog.domain.stock.common.type.Period;
import com.stocklog.infra.gemini.service.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
@Slf4j
public class StockApiController {

    private final StockAnalysisService stockAnalysisService;
    private final StockFacadeService stockFacadeService;
    private final GeminiService geminiService;


    @GetMapping("/search")
    public List<StockDashBoard> searchStocks(
            @RequestParam String name,
            @PageableDefault(size = 5) Pageable pageable) {
        return stockFacadeService.getStockDashBoardsByKeyword(name, pageable);
    }

    @GetMapping("/{code}")
    public StockPriceDto getPrice(@PathVariable String code) {
        return stockFacadeService.getPrice(code);
    }

    @GetMapping("/{code}/info")
    public StockInfoDto getInfo(@PathVariable String code) {
        return stockFacadeService.getInfo(code);
    }

    @GetMapping("/{code}/detail")
    public StockSnapshot getStockDetail(@PathVariable String code) {
        return stockFacadeService.getStockSnapShot(code);
    }

    @GetMapping("/{code}/chart")
    public StockChartListDto getStockChart(
            @PathVariable String code,
            @RequestParam(defaultValue = "DAY") Period period) {
        log.info("getStockChart: [{}][{}]", code, period);
        return stockFacadeService.getChartList(code, period);
    }

    @GetMapping("/{code}/financial")
    public StockFinancialDto getFinancialsResponse(@PathVariable String code) {
        return stockFacadeService.getFinaincial(code);
    }

//    @GetMapping("/{code}/earning")
//    public StockEarningResponse getEarningResponse(@PathVariable String code) {
//        return stockAnalysisService.fetchEarning(code);
//    }

    @GetMapping("/{code}/analysis")
    public StockAnalysisResult getAnalysis(@PathVariable String code) {
        return stockAnalysisService.getAnalysisResult(stockFacadeService.getStockSnapShot(code));
    }


    @GetMapping("/{code}/ai-comment")
    public ResponseEntity<String> getAiComment(@PathVariable String code) {
        StockAnalysisResult analysisResult = stockAnalysisService.getAnalysisResult(stockFacadeService.getStockSnapShot(code));
        String comment = geminiService.getStockAnalysis(analysisResult);
        return ResponseEntity.ok(comment);
    }

}