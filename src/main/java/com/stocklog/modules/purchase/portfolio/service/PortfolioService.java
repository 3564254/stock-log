package com.stocklog.modules.purchase.portfolio.service;

import com.stocklog.modules.member.application.usecase.GetMemberUseCase;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.purchase.portfolio.dto.request.HoldingUpdateRequest;
import com.stocklog.modules.purchase.portfolio.dto.response.HoldingResponse;
import com.stocklog.modules.purchase.portfolio.dto.response.PortfolioResponse;
import com.stocklog.modules.purchase.portfolio.entity.Holding;
import com.stocklog.modules.purchase.portfolio.repository.HoldingRepository;
import com.stocklog.modules.stock.application.usecase.StockSummaryUseCase;
import com.stocklog.modules.stock.info.dto.StockInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
class PortfolioService {

    private final HoldingRepository holdingRepository;
    private final StockSummaryUseCase summaryUseCase;
    private final GetMemberUseCase memberUsecase;

    PortfolioResponse getMyPortfolio(Long memberId) {
        // TODO(JPA): HoldingRepository의 EntityGraph 덕분에 holding -> stock의 DB N+1은 피하고 있다.
        // 하지만 아래 루프에서 종목마다 stockReader를 호출하므로 보유 종목 수만큼 추가 조회/API 호출이 발생한다.
        // 화면에 필요한 현재가를 벌크 조회하는 query/use-case로 바꾼 뒤, SQL·외부 호출 횟수를 측정해 보자.
        List<Holding> holdings = holdingRepository.findAllByMemberId(memberId);

        List<HoldingResponse> holdingResponses = holdings.stream()
                .map(holding -> {
                    BigDecimal currentPrice = summaryUseCase.getStockSummary(holding.getStock().getCode()).price().currentPrice();
                    return HoldingResponse.of(holding, currentPrice);
                })
                .toList();

        return PortfolioResponse.of(holdingResponses);
    }

    @Transactional
    public void updateHolding(Long memberId, HoldingUpdateRequest request) {

        holdingRepository.findByMemberIdAndStockCode(memberId, request.code())
                .ifPresentOrElse(
                        holding -> holding.updateAveragePrice(
                                request.averagePrice()
                        ),
                        () -> saveNewHolding(memberId, request)
                );
    }

    @Transactional
    public void deleteHolding(Long memberId, String stockCode) {
        holdingRepository.deleteByMemberIdAndStockCode(memberId, stockCode);
    }


    private void saveNewHolding(Long memberId, HoldingUpdateRequest request) {
        Member member = memberUsecase.getMemberReference(memberId);
        StockInfoDto info = summaryUseCase.getStockSummary(request.code()).info();
        // TODO(JPA): DTO를 toEntity로 바꾸면 영속성 컨텍스트가 관리하지 않는 새 StockInfo가 만들어진다.
        // 연관관계에는 이미 존재하는 StockInfo의 managed reference(getReferenceById 등)를 넣어야 한다.
        // 그래야 transient/detached entity 문제와 불필요한 INSERT 가능성을 피할 수 있다.
        holdingRepository.save(Holding.builder()
                .member(member)
                .stock(StockInfoDto.toEntity(info))
                .averagePrice(request.averagePrice())
                .build());
    }
}
