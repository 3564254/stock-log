package com.stocklog.web.api;

import com.stocklog.modules.ai.AiProvider;
import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.finance.model.ExchangeSession;
import com.stocklog.modules.common.type.ChartDataType;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.member.dto.response.MemberResponse;
import com.stocklog.modules.purchase.portfolio.analysis.dto.PortfolioAnalysisResult;
import com.stocklog.modules.purchase.portfolio.dto.request.AddPortfolioStockRequest;
import com.stocklog.modules.purchase.portfolio.dto.request.HoldingUpdateRequest;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioAnalysisResponse;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.portfolio.service.PortfolioFacadeService;
import com.stocklog.modules.purchase.trade.dto.request.TradingRuleSaveRequest;
import com.stocklog.modules.purchase.trade.dto.request.WatchItemRequest;
import com.stocklog.modules.purchase.trade.dto.response.TradingRuleCheckResponse;
import com.stocklog.modules.purchase.trade.service.TradeFacadeService;
import com.stocklog.modules.stock.analysis.HoldingStopService;
import com.stocklog.modules.stock.analysis.StockAnalysisService;
import com.stocklog.modules.stock.analysis.signal.trading.TradingSignalResult;
import com.stocklog.modules.stock.application.usecase.GetStockSignalUseCase;
import com.stocklog.modules.stock.chart.StockChartService;
import com.stocklog.modules.stock.chart.dto.StockChartDto;
import com.stocklog.global.annotation.LoginUser;
import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioApiController {

    private final StockChartService chartService;

    private final GetStockSignalUseCase stockSignalUseCase;

    private final StockAnalysisService analysisService;
    private final HoldingStopService holdingStopService;
    private final PortfolioFacadeService portfolioService;
    private final TradeFacadeService tradeService;
    private final AiProvider provider;

    // 💡 1. 포트폴리오 종목 관리 (GET, POST 통일)
    @PostMapping("/holdings")
    public void addStock(@LoginUser MemberResponse loginMember, @RequestBody @Valid AddPortfolioStockRequest request) {
        portfolioService.updateHolding(loginMember.getId(), HoldingUpdateRequest.stockToHoldings(request));
    }

    @PostMapping("/holdings/update")
    public void updateStock(@LoginUser MemberResponse loginMember, @RequestBody @Valid HoldingUpdateRequest request) {
        portfolioService.updateHolding(loginMember.getId(), request);
    }

    @PostMapping("/holdings/delete")
    public void deleteStock(@LoginUser MemberResponse loginMember, @RequestParam String code) {
        portfolioService.deleteHolding(loginMember.getId(), code);
    }

    // 💡 2. 관심 종목 및 규칙 관리
    @PostMapping("/watchlist")
    public void addWatchList(@LoginUser MemberResponse loginMember, @RequestBody WatchItemRequest request) {
        tradeService.addWatchItem(loginMember.getId(), request.stockCode());
    }

    @PostMapping("/watchList/delete")
    public void deleteWatchList(@LoginUser MemberResponse loginMember,@RequestParam String stockCode) {
        tradeService.deleteWatchlistItem(loginMember.getId(), stockCode);
    }

    @PostMapping("/rules")
    public void addRules(@LoginUser MemberResponse loginMember, @RequestBody TradingRuleSaveRequest request) {
        tradeService.saveTradingRule(loginMember.getId(), request);
    }

    @PostMapping("/rules/delete")
    public void deleteRule(@LoginUser MemberResponse loginMember, @RequestParam Long tradeRuleId) {
        tradeService.deleteTradingRule(loginMember.getId(), tradeRuleId);
    }

    @PostMapping("/rules/{ruleId}/toggle")
    public void toggleRule(@LoginUser MemberResponse loginMember, @PathVariable Long ruleId, @RequestBody Map<String, Boolean> request) {
        if (loginMember == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        Boolean targetState = request.get("isActive");
        tradeService.toggleRule(loginMember.getId(), ruleId, targetState);
    }

    // 💡 3. AI 관련 API
    @GetMapping("/ai-comment")
    public String getAiComment(@LoginUser MemberResponse loginMember, @RequestParam String code) {
        PortfolioResponse myPortfolio = portfolioService.getMyPortfolio(loginMember.getId());
        PortfolioAnalysisResponse analysis = portfolioService.analyzePortfolio(myPortfolio);
        return provider.getPortfolioAnalysis(PortfolioAnalysisResult.of(myPortfolio.myStocks(), analysis));
    }

    /**
     * 보유 종목별 '보유 기준 손절선' (차트 국면과 상관없이 지지 구간·최근 저점·변동폭으로 정함).
     * 화면을 먼저 띄운 뒤 따로 불러오고, 서버에서 종목당 30분간 재사용한다.
     * 예: /api/portfolio/holding-stops?codes=005930,AAPL
     */
    @GetMapping("/holding-stops")
    public Map<String, HoldingStopService.HoldingStopDto> getHoldingStops(@LoginUser MemberResponse loginMember,
                                                                         @RequestParam(defaultValue = "") String codes) {
        if (loginMember == null) throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        return holdingStopService.getStops(java.util.Arrays.asList(codes.split(",")));
    }

    @PostMapping("/verify/buy")
    public TradingRuleCheckResponse verifyBuy(@LoginUser MemberResponse loginMember, @RequestBody Map<String, String> request) {
        String stockCode = request.get("targetId");

        List<StockChartDto> dailyCharts = chartService.getDailyChartDtoAsc(stockCode);
        List<StockChartDto> weeklyCharts = chartService.getWeeklyChartDtoAsc(stockCode);

        MarketContext marketContext = MarketContext.of(dailyCharts, weeklyCharts, ChartDataType.STOCK,
                ExchangeSession.fromCode(stockCode));

        // 타점 엔진 한 번만 호출해서 배지(국면)·진입가·검증 판정이 같은 결과를 보도록 한다
        TradingSignalResult signal = analysisService.getTradingSignal(stockCode, marketContext);
        ChartPhase chartPhase = signal.analysis().phase();

        tradeService.updateAnalyzedSignal(loginMember.getId(), stockCode, chartPhase, signal.priceTarget().primaryEntry());
        return tradeService.evaluateBuySignal(loginMember.getId(), marketContext, signal);
    }

    @PostMapping("/verify/sell")
    public Map<String, Object> verifySell(@LoginUser MemberResponse loginMember, @RequestBody Map<String, String> request) {
        // TODO: 매도 검증 로직 구현 후 반환
        return Map.of("status", "HOLD", "feedback", "아직 추세가 꺾이지 않았으므로 홀딩을 권장합니다.");
    }
}