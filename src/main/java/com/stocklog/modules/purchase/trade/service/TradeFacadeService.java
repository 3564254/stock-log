package com.stocklog.modules.purchase.trade.service;

import com.stocklog.modules.common.finance.context.MarketContext;
import com.stocklog.modules.common.type.ChartPhase;
import com.stocklog.modules.purchase.trade.dto.request.TradeLogRequest;
import com.stocklog.modules.purchase.trade.dto.request.TradeReviewRequest;
import com.stocklog.modules.purchase.trade.dto.request.TradingRuleSaveRequest;
import com.stocklog.modules.purchase.trade.dto.response.*;
import com.stocklog.modules.purchase.trade.dto.vo.WatchItemDto;
import com.stocklog.modules.purchase.trade.entity.SortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TradeFacadeService {

    private final TradeLogService tradeLogService;
    private final TradeReviewService tradeReviewService;
    private final TradingRuleService tradingRuleService;
    private final WatchlistService watchlistService;

    // ================= Trade Log  =================

    public TradeLogResponse getMonthlyTradeLogs(Long memberId, LocalDate targetDate, SortType type) {
        return tradeLogService.getMonthlyTradeLogs(memberId, targetDate, type);
    }

    public void saveTradeLog(Long memberId, TradeLogRequest request) {
        tradeLogService.saveTradeLog(memberId, request);
    }

    // ================= Trade Review  =================

    public List<TradeReviewResponse> getMonthlyReviews(Long memberId, LocalDate targetDate) {
        return tradeReviewService.getMonthlyReviews(memberId, targetDate);
    }

    public void saveReview(Long memberId, TradeReviewRequest request) {
        tradeReviewService.saveReview(memberId, request);
    }

    public void updateReview(Long memberId, Long reviewId, TradeReviewRequest request) {
        tradeReviewService.updateReview(memberId, reviewId, request);
    }

    public void deleteReview(Long memberId, Long reviewId) {
        tradeReviewService.deleteReview(memberId, reviewId);
    }

    // ================= Trade Rule  ================= //

    public TradingRuleResponse getActiveRules(Long memberId) {
        return tradingRuleService.getActiveRules(memberId);
    }

    public void toggleRule(Long memberId, Long ruleId, Boolean targetState) {
        tradingRuleService.toggleRule(memberId, ruleId, targetState);
    }

    public void deleteTradingRule(Long memberId, Long tradingRuleId) {
        tradingRuleService.deleteRule(memberId, tradingRuleId);
    }


    public TradingRuleCheckResponse evaluateBuySignal(Long memberId, MarketContext marketContext, ChartPhase phase) {
        return tradingRuleService.evaluateBuySignal(memberId, marketContext, phase);
    }

    public void saveTradingRule(Long memberId, TradingRuleSaveRequest request) {
        tradingRuleService.addTradingRule(memberId, request);
    }

    // ================= Watch List  =================
    public WatchListResponse getWatchlist(Long memberId) {
        List<WatchItemDto> watchlist = watchlistService.getWatchlist(memberId);
        return WatchListResponse.builder()
                .memberId(memberId)
                .itemResponseList(watchlist)
                .build();
    }

    public void addWatchItem(Long memberId, String stockCode) {
        watchlistService.addWatchList(memberId, stockCode);
    }

    public WatchItemDto getWatchlistItem(Long memberId, String stockCode) {
        return watchlistService.getWatchlistItem(memberId, stockCode);
    }

    public void deleteWatchlistItem(Long memberId, String stockCode) {
        watchlistService.deleteWatchlistItem(memberId, stockCode);
    }

    public void updateAnalyzedSignal(Long memberId, String stockCode, ChartPhase phase, double targetPrice) {
        watchlistService.updateAnalyzedSignal(memberId, stockCode, phase, targetPrice);
    }



}
