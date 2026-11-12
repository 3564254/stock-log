package com.stocklog.modules.purchase.portfolio.service;

import com.stocklog.modules.market.exchange.dto.ExchangeRateInfo;
import com.stocklog.modules.market.service.ExchangeService;
import com.stocklog.modules.member.application.usecase.GetMemberUseCase;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.purchase.portfolio.dto.request.HoldingUpdateRequest;
import com.stocklog.modules.purchase.portfolio.dto.response.HoldingResponse;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.portfolio.entity.Holding;
import com.stocklog.modules.purchase.portfolio.repository.HoldingRepository;
import com.stocklog.modules.purchase.trade.entity.TradeLog;
import com.stocklog.modules.purchase.trade.repository.TradeLogRepository;
import com.stocklog.modules.stock.application.usecase.StockSummaryUseCase;
import com.stocklog.modules.stock.analysis.HoldingStopService;
import com.stocklog.modules.stock.info.dto.StockInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class PortfolioService {

    private final HoldingRepository holdingRepository;
    private final StockSummaryUseCase summaryUseCase;
    private final GetMemberUseCase memberUsecase;
    private final ExchangeService exchangeService;
    private final HoldingStopService holdingStopService;
    private final HoldingPeakTracker peakTracker;
    private final TradeLogRepository tradeLogRepository;

    PortfolioResponse getMyPortfolio(Long memberId) {
        List<Holding> holdings = holdingRepository.findAllByMemberId(memberId);

        java.util.Map<Long, BigDecimal> prices = new java.util.HashMap<>();
        List<HoldingResponse> holdingResponses = holdings.stream()
                .map(holding -> {
                    BigDecimal cur = fetchCurrentPrice(holding.getStock().getCode());
                    // 매수 후 최고가 = max. 화면을 안 연 날의 고점도 반영된다
                    BigDecimal peak = peakOf(holding, cur);
                    if (peak != null) prices.put(holding.getId(), peak);
                    HoldingResponse r = HoldingResponse.of(holding, cur);
                    return peak != null ? r.toBuilder().highestPriceSinceBuy(peak).build() : r;
                })
                .toList();
        trackPeaks(prices);

        // 수량을 입력한 달러 종목이 있을 때만 환율을 가져온다
        boolean needsFx = holdingResponses.stream().anyMatch(h -> h.marketValue() != null && !h.domestic());
        return PortfolioResponse.of(holdingResponses, needsFx ? fetchUsdKrw() : null);
    }

    // 외부 시세 API가 한 종목에서 실패해도 화면 전체가 오류가 나지 않도록, 그 종목만 현재가 없음(null)으로 둔다
    private BigDecimal fetchCurrentPrice(String code) {
        try {
            return summaryUseCase.getStockSummary(code).price().currentPrice();
        } catch (RuntimeException e) {
            log.warn("[Portfolio] {} 현재가 조회 실패, 이 종목만 비워 둠: {}", code, e.getMessage());
            return null;
        }
    }

    // 매수 후 최고가 갱신은 부가 기능이라 실패해도 화면은 그대로 보여 준다
    /** 매수 후 최고가 = 보유 시작일 이후 일봉 종가 최고 (본전 이동 판단용) */
    private BigDecimal peakOf(Holding holding, BigDecimal cur) {
        double hc = holdingStopService.highestCloseSince(holding.getStock().getCode(), holding.basisDateOrCreated());
        if (hc > 0) return BigDecimal.valueOf(hc);
        return holding.getHighestPriceSinceBuy();
    }

    private void trackPeaks(java.util.Map<Long, BigDecimal> prices) {
        try {
            peakTracker.track(prices);
        } catch (RuntimeException e) {
            log.warn("[Portfolio] 매수 후 최고가 갱신 실패: {}", e.getMessage());
        }
    }

    /** 평단이 바뀔 때 새 '매수 때 손절가' */
    private BigDecimal entryStopForNewBasis(BigDecimal oldAvg, HoldingUpdateRequest request) {
        BigDecimal plan = planStopOf(request.code());
        BigDecimal keep = request.keepStop();
        boolean pyramid = keep != null && keep.signum() > 0 && oldAvg != null
                && request.averagePrice() != null && request.averagePrice().compareTo(oldAvg) > 0
                && keep.compareTo(oldAvg) >= 0;
        if (!pyramid) return plan;
        return plan == null || keep.compareTo(plan) > 0 ? keep : plan;
    }

    /** 현재 진입 계획의 손절가 (없으면 null) */
    private BigDecimal planStopOf(String code) {
        try {
            var dto = holdingStopService.getStops(List.of(code)).get(code);
            return dto != null && dto.planStop() > 0 ? BigDecimal.valueOf(dto.planStop()) : null;
        } catch (RuntimeException e) {
            log.warn("[Portfolio] {} 매수 시점 손절가 계산 실패, 비워 둠: {}", code, e.getMessage());
            return null;
        }
    }

    // 환율을 못 가져와도 포트폴리오 화면은 떠야 하므로 실패는 null로 넘긴다
    private BigDecimal fetchUsdKrw() {
        try {
            ExchangeRateInfo fx = exchangeService.fetchExchangeRate();
            return (fx != null && fx.baseRate() != null && fx.baseRate().signum() > 0) ? fx.baseRate() : null;
        } catch (RuntimeException e) {
            log.warn("포트폴리오 비중 계산용 환율 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public void updateHolding(Long memberId, HoldingUpdateRequest request) {

        holdingRepository.findByMemberIdAndStockCode(memberId, request.code())
                .ifPresentOrElse(
                        holding -> {
                            // 수량을 줄이고 판 가격을 넘겼으면 줄어든 만큼을 기존 평단 기준으로 매매 기록에 남긴다 (평단을 바꾸기 전에)
                            if (request.soldPrice() != null && holding.getQuantity() != null && request.quantity() != null
                                    && request.quantity().compareTo(holding.getQuantity()) < 0) {
                                recordSale(memberId, holding, holding.getQuantity().subtract(request.quantity()), request.soldPrice());
                            }
                            boolean newBasis = request.averagePrice() != null
                                    && (holding.getAveragePrice() == null || holding.getAveragePrice().compareTo(request.averagePrice()) != 0);
                            if (newBasis) {
                                holding.resetForNewBasis(request.averagePrice(), entryStopForNewBasis(holding.getAveragePrice(), request));
                            } else {
                                holding.updateAveragePrice(request.averagePrice());
                            }
                            holding.updateQuantity(request.quantity());
                            if (request.holdStyle() != null) holding.updateHoldStyle(request.holdStyle());
                        },
                        () -> saveNewHolding(memberId, request)
                );
    }

    @Transactional
    public void deleteHolding(Long memberId, String stockCode, BigDecimal soldPrice) {
        if (soldPrice != null && soldPrice.signum() > 0) {
            holdingRepository.findByMemberIdAndStockCode(memberId, stockCode)
                    .ifPresent(h -> recordSale(memberId, h, h.getQuantity(), soldPrice));
        }
        holdingRepository.deleteByMemberIdAndStockCode(memberId, stockCode);
    }

    /** 판 기록을 매매 기록(TradeLog)에 자동으로 남긴다 */
    private void recordSale(Long memberId, Holding h, BigDecimal qty, BigDecimal price) {
        if (qty == null || qty.signum() <= 0 || h.getAveragePrice() == null || h.getAveragePrice().signum() <= 0) return;
        BigDecimal profit = price.subtract(h.getAveragePrice()).multiply(qty);
        if (!HoldingResponse.of(h, price).domestic()) {
            BigDecimal fx = fetchUsdKrw();
            if (fx == null) {
                log.warn("[Portfolio] {} 판 기록: 환율을 못 가져와 매매 기록을 남기지 않음", h.getStock().getCode());
                return;
            }
            profit = profit.multiply(fx);
        }
        tradeLogRepository.save(TradeLog.builder()
                .member(memberUsecase.getMemberReference(memberId))
                .stock(h.getStock())
                .quantity(qty)
                .tradeDate(LocalDate.now())
                .profit(profit.setScale(0, RoundingMode.HALF_UP))
                .build());
    }

    private void saveNewHolding(Long memberId, HoldingUpdateRequest request) {
        Member member = memberUsecase.getMemberReference(memberId);
        StockInfoDto info = summaryUseCase.getStockSummary(request.code()).info();
        // TODO: 기존 StockInfo 엔티티를 재사용하도록 변경
        Holding holding = Holding.builder()
                .member(member)
                .stock(StockInfoDto.toEntity(info))
                .averagePrice(request.averagePrice())
                .quantity(request.quantity())
                .highestPriceSinceBuy(request.averagePrice())
                .entryStop(planStopOf(request.code()))
                .build();
        holding.updateHoldStyle(request.holdStyle());
        holdingRepository.save(holding);
    }
}
