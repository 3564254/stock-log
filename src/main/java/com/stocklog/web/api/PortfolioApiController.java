package com.stocklog.web.api;

import com.stocklog.modules.ai.AiException;
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
import com.stocklog.modules.purchase.portfolio.review.PortfolioAiReviewService;
import com.stocklog.modules.purchase.portfolio.review.ReviewFacts;
import com.stocklog.modules.purchase.portfolio.service.PortfolioFacadeService;
import com.stocklog.modules.purchase.portfolio.setting.InvestmentSettingService;
import com.stocklog.modules.purchase.portfolio.setting.RiskProfile;
import com.stocklog.modules.purchase.trade.dto.request.TradingRuleSaveRequest;
import com.stocklog.modules.purchase.trade.dto.request.WatchItemRequest;
import com.stocklog.modules.purchase.trade.dto.response.TradingRuleCheckResponse;
import com.stocklog.modules.purchase.trade.service.TradeFacadeService;
import com.stocklog.modules.stock.analysis.HoldingStopService;
import com.stocklog.modules.stock.analysis.PortfolioCheckService;
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
import org.springframework.http.ResponseEntity;
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
    private final com.stocklog.modules.stock.application.usecase.StockSnapshotUseCase snapshotUseCase;
    private final HoldingStopService holdingStopService;
    private final PortfolioCheckService portfolioCheckService;
    private final PortfolioFacadeService portfolioService;
    private final InvestmentSettingService settingService;
    private final TradeFacadeService tradeService;
    private final AiProvider provider;
    private final PortfolioAiReviewService reviewService;

    // 포트폴리오 종목 관리 (GET, POST 통일)
    @PostMapping("/holdings")
    public void addStock(@LoginUser MemberResponse loginMember, @RequestBody @Valid AddPortfolioStockRequest request) {
        portfolioService.updateHolding(loginMember.getId(), HoldingUpdateRequest.stockToHoldings(request));
    }

    @PostMapping("/holdings/update")
    public void updateStock(@LoginUser MemberResponse loginMember, @RequestBody @Valid HoldingUpdateRequest request) {
        portfolioService.updateHolding(loginMember.getId(), request);
    }

    @PostMapping("/holdings/delete")
    public void deleteStock(@LoginUser MemberResponse loginMember, @RequestParam String code,
                            @RequestParam(required = false) java.math.BigDecimal soldPrice) {
        // soldPrice가 있으면 '팔았어요': 남은 수량 전부를 그 가격에 판 걸로 보고 실현 손익을 매매 기록에 남긴 뒤 지운다
        portfolioService.deleteHolding(loginMember.getId(), code, soldPrice != null && soldPrice.signum() > 0 ? soldPrice : null);
    }

    // 관심 종목 및 규칙 관리
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

    // AI 관련 API
    @GetMapping("/ai-comment")
    public String getAiComment(@LoginUser MemberResponse loginMember, @RequestParam String code) {
        PortfolioResponse myPortfolio = portfolioService.getMyPortfolio(loginMember.getId());
        PortfolioAnalysisResponse analysis = portfolioService.analyzePortfolio(myPortfolio);
        return provider.getPortfolioAnalysis(PortfolioAnalysisResult.of(myPortfolio.myStocks(), analysis));
    }

    /** 종목 분석 화면의 '내 보유 대응'용: 이 종목을 갖고 있으면 평단·수량·비중·매수 때 손절가·매수 후 최고가를 돌려준다 */
    @GetMapping("/my-holding")
    public Map<String, Object> getMyHolding(@LoginUser MemberResponse loginMember, @RequestParam String code) {
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("holding", null);
        if (loginMember == null || code == null || code.isBlank()) return out;
        PortfolioResponse p = portfolioService.getMyPortfolio(loginMember.getId());
        if (p == null || p.myStocks() == null) return out;
        InvestmentSettingService.InvestmentStatus st = settingService.status(loginMember.getId(), p);
        out.put("setting", st);
        p.myStocks().stream()
                .filter(h -> code.trim().equalsIgnoreCase(h.stockCode()))
                .findFirst()
                .ifPresent(h -> {
                    out.put("holding", h);
                    // 계좌 비중: 운용 자금을 넣었으면 (이 종목 원화 평가액) / 계좌(운용 자금 + 손익)
                    if (st.accountKnown() && h.weight() != null && p.totalMarketValueKrw() != null && st.totalAssetsKrw() != null
                            && st.totalAssetsKrw().signum() > 0) {
                        java.math.BigDecimal krw = p.totalMarketValueKrw().multiply(h.weight()).divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                        out.put("accountWeight", krw.multiply(java.math.BigDecimal.valueOf(100)).divide(st.totalAssetsKrw(), 2, java.math.RoundingMode.HALF_UP));
                    }
                });
        out.put("holdingCount", p.myStocks().size());
        out.put("weightedCount", p.weightedCount());
        return out;
    }

    /** 포트폴리오 점검: 보유 종목끼리의 상관·실질 종목 수·변동성·베타. 비중은 원화 평가액 비중 */
    /** 마지막 AI 주간 점검 (이번 주 것이 아니어도). 없으면 204 */
    @GetMapping("/ai-review")
    public ResponseEntity<?> getAiReview(@LoginUser MemberResponse loginMember) {
        if (loginMember == null) throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        return reviewService.latest(loginMember.getId()).<ResponseEntity<?>>map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    /** AI 주간 점검 받기 */
    @PostMapping("/ai-review")
    public ResponseEntity<?> createAiReview(@LoginUser MemberResponse loginMember, @RequestBody ReviewFacts facts) {
        if (loginMember == null) throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        if (facts == null || facts.holdings() == null || facts.holdings().isEmpty()) {
            return ResponseEntity.unprocessableEntity().body(Map.of("code", "EMPTY", "message", "보유 종목이 있어야 점검할 수 있어요."));
        }
        try {
            return ResponseEntity.ok(reviewService.generate(loginMember.getId(), facts));
        } catch (AiException e) {
            return ResponseEntity.status(e.isQuota() ? 429 : 502).body(Map.of("code", e.code(), "message", e.userMessage()));
        }
    }

    @GetMapping("/check")
    public PortfolioCheckService.Check getPortfolioCheck(@LoginUser MemberResponse loginMember) {
        if (loginMember == null) throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        PortfolioResponse p = portfolioService.getMyPortfolio(loginMember.getId());
        Map<String, Double> w = new java.util.LinkedHashMap<>();
        if (p != null && p.myStocks() != null) {
            boolean anyWeight = p.myStocks().stream().anyMatch(h -> h.weight() != null);
            p.myStocks().forEach(h -> w.put(h.stockCode(), anyWeight ? (h.weight() != null ? h.weight().doubleValue() : 0.0) : 1.0));
        }
        return portfolioCheckService.check(w);
    }

    /** 투자 설정(성향·현금)과 계좌 상태 */
    @GetMapping("/settings")
    public InvestmentSettingService.InvestmentStatus getSettings(@LoginUser MemberResponse loginMember) {
        if (loginMember == null) throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        return settingService.status(loginMember.getId(), portfolioService.getMyPortfolio(loginMember.getId()));
    }

    /** 투자 설정 변경 요청 */
    @PostMapping("/settings")
    public InvestmentSettingService.InvestmentStatus saveSettings(@LoginUser MemberResponse loginMember, @RequestBody Map<String, Object> body) {
        if (loginMember == null) throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        Object cash = body.get("capitalKrw");
        java.math.BigDecimal cashKrw = null;
        if (cash != null && !String.valueOf(cash).isBlank()) {
            try { cashKrw = new java.math.BigDecimal(String.valueOf(cash).replace(",", "").trim()); }
            catch (NumberFormatException e) { throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); }
        }
        settingService.save(loginMember.getId(), RiskProfile.parse(String.valueOf(body.get("profile"))), cashKrw,
                Boolean.TRUE.equals(body.get("resetPeak")));
        return settingService.status(loginMember.getId(), portfolioService.getMyPortfolio(loginMember.getId()));
    }

    /** 보유 종목별 '보유 기준 손절선' */
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
        // 점수 조건(종합·밸류·모멘텀·기술) 규칙이 있을 때만 종목 스냅샷을 불러와 점수를 계산한다
        return tradeService.evaluateBuySignal(loginMember.getId(), marketContext, signal,
                () -> analysisService.getScoreBreakdown(snapshotUseCase.getStockSnapshot(stockCode)));
    }

    @PostMapping("/verify/sell")
    public Map<String, Object> verifySell(@LoginUser MemberResponse loginMember, @RequestBody Map<String, String> request) {
        // TODO: 매도 검증 로직 구현 후 반환
        return Map.of("status", "HOLD", "feedback", "아직 추세가 꺾이지 않았으므로 홀딩을 권장합니다.");
    }
}