package com.stocklog.web.api;

import com.stocklog.domain.stock.dto.response.*;
import com.stocklog.domain.stock.analysis.result.StockAnalysisResult;
import com.stocklog.domain.stock.dto.vo.price.StockChartData;
import com.stocklog.domain.stock.service.*;
import com.stocklog.domain.stock.type.Period;
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

    private final StockService stockService;
    private final StockPriceService stockPriceService;
    private final StockInfoService stockInfoService;
    private final StockChartService stockChartService;
    private final StockAnalysisService stockAnalysisService;
    private final GeminiService geminiService;

    @GetMapping("/search")
    public List<StockPriceResponse> searchStocks(
            @RequestParam String name,
            @PageableDefault(size = 5) Pageable pageable) {
        return stockPriceService.fetchPriceByKeyword(name, pageable);
    }



    @GetMapping("/{code}")
    public StockPriceResponse getPrice(@PathVariable String code) {
        return stockPriceService.fetchPrice(code);
    }

    @GetMapping("/{code}/info")
    public StockInfoResponse getInfo(@PathVariable String code) {
        return stockInfoService.fetchInfo(code);
    }

    @GetMapping("/{code}/detail")
    public StockDetailResponse getStockDetail(@PathVariable String code) {
        return StockDetailResponse.of(
                stockService.getStock(code),
                stockInfoService.fetchInfoDetail(code),
                stockPriceService.fetchPriceDetail(code)
        );
    }

    @GetMapping("/{code}/chart")
    public StockChartResponse getStockChart(
            @PathVariable String code,
            @RequestParam(defaultValue = "DAY") Period period) {
        List<StockChartData> chartDataList = stockChartService.getChartData(code, period);
        return StockChartResponse.builder()
                .code(code)
                .chartDataList(chartDataList)
                .build();
    }

    @GetMapping("/{code}/financial")
    public StockFinancialsResponse getFinancialsResponse(@PathVariable String code) {
        return stockAnalysisService.fetchFinancial(code);
    }

    @GetMapping("/{code}/earning")
    public StockEarningResponse getEarningResponse(@PathVariable String code) {
        return stockAnalysisService.fetchEarning(code);
    }

    @GetMapping("/{code}/analysis")
    public StockAnalysisResult getAnalysis(@PathVariable String code) {
        return stockAnalysisService.analyze(code);
    }


    @GetMapping("/{code}/ai-comment")
    public ResponseEntity<String> getAiComment(@PathVariable String code) {
        StockAnalysisResult analysis = stockAnalysisService.analyze(code);
        String comment = geminiService.getStockAnalysis(analysis);
        return ResponseEntity.ok(comment);
    }

}