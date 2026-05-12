package com.stocklog.domain.portfolio.service;

import com.stocklog.domain.marketdata.exchange.service.ExchangeService;
import com.stocklog.domain.member.entity.Member;
import com.stocklog.domain.member.service.MemberService;
import com.stocklog.domain.portfolio.dto.response.HoldingResponse;
import com.stocklog.domain.portfolio.dto.request.HoldingUpdateRequest;
import com.stocklog.domain.portfolio.dto.response.PortfolioResponse;
import com.stocklog.domain.portfolio.entity.Holding;
import com.stocklog.domain.portfolio.repository.HoldingRepository;
import com.stocklog.domain.application.StockFacadeService;
import com.stocklog.domain.stock.info.dto.internal.StockInfoDto;
import com.stocklog.domain.stock.common.type.Currency;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioService {

    private final HoldingRepository holdingRepository;
    private final StockFacadeService stockFacadeService;
    private final MemberService memberService;
    private final ExchangeService exchangeService;

    public PortfolioResponse getMyPortfolio(Long memberId) {
        List<Holding> holdings = holdingRepository.findAllByMemberId(memberId);
        if (holdings.isEmpty()) {
            return PortfolioResponse.createFallback();
        }

        List<HoldingResponse> holdingResponses = holdings.stream()
                .map(holding -> {

                    BigDecimal currentPrice = fetchCurrentPrice(holding.getStock().getCode());
                    BigDecimal currentExchangeRate = fetchCurrentExchangeRate(holding.getCurrency());

                    return HoldingResponse.of(holding, currentPrice, currentExchangeRate);
                })
                .toList();

        return PortfolioResponse.of(holdingResponses);
    }

    public Holding getHolding(Long holdingId) {
        return holdingRepository.findById(holdingId).orElseThrow();
    }

    @Transactional
    public void updateHolding(Long memberId, HoldingUpdateRequest request) {
        // 파라미터로 받은 memberId를 사용하여 조회
        holdingRepository.findByMemberIdAndStockCode(memberId, request.code())
                .ifPresentOrElse(
                        holding -> holding.updateHolding(
                                request.quantity(),
                                request.purchasePrice(),
                                request.purchaseExchangeRate()
                        ),
                        () -> saveNewHolding(memberId, request) // memberId 전달
                );
    }

    @Transactional
    public void deleteHolding(Long memberId, String stockCode) {
        // 안전하게 세션 ID와 종목 코드로 삭제
        holdingRepository.deleteByMemberIdAndStockCode(memberId, stockCode);
    }

    private BigDecimal fetchCurrentPrice(String stockCode) {
        return stockFacadeService.getStockSummary(stockCode).price().currentPrice();
    }

    private BigDecimal fetchCurrentExchangeRate(Currency currency) {
        if (currency == Currency.KRW) {
            return BigDecimal.ONE;
        }
        return exchangeService.fetchExchangeRate().baseRate();
    }

    private void saveNewHolding(Long memberId, HoldingUpdateRequest request) {
        Member member = memberService.getReference(memberId);
        StockInfoDto info = stockFacadeService.getStockSummary(request.code()).info();
        holdingRepository.save(Holding.builder()
                .member(member)
                .stock(StockInfoDto.toEntity(info))
                .quantity(request.quantity())
                .purchasePrice(request.purchasePrice())
                .purchaseExchangeRate(request.purchaseExchangeRate())
                .currency(info.currency())
                .build());
    }
}