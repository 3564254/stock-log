package com.stocklog.modules.purchase.trade.service;

import com.stocklog.modules.member.application.usecase.GetMemberUseCase;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.stock.application.usecase.GetStockInfoUseCase;
import com.stocklog.modules.stock.info.StockInfo;
import com.stocklog.modules.purchase.trade.dto.request.TradeLogRequest;
import com.stocklog.modules.purchase.trade.dto.response.TradeLogResponse;
import com.stocklog.modules.purchase.trade.entity.SortType;
import com.stocklog.modules.purchase.trade.entity.TradeLog;
import com.stocklog.modules.purchase.trade.repository.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TradeLogService {
    private final TradeLogRepository tradeLogRepository;
    private final GetMemberUseCase memberUseCase;
    private final GetStockInfoUseCase stockUseCase;

    TradeLogResponse getMonthlyTradeLogs(Long memberId, LocalDate targetDate, SortType type) {
        Member member = memberUseCase.getMemberReference(memberId);

        LocalDate startDate = targetDate.withDayOfMonth(1);
        LocalDate endDate = startDate.plusMonths(1);

        Sort sort = SortType.getSort(type);

        List<TradeLog> tradeLogs = tradeLogRepository.findMonthlyTradeLogs(member, startDate, endDate, sort);
        return TradeLogResponse.of(targetDate, tradeLogs);
    }

    @Transactional
    void saveTradeLog(Long memberId, TradeLogRequest request) {
        Member member = memberUseCase.getMemberReference(memberId);
        StockInfo stock = stockUseCase.getStockInfo(request.stockCode());

        tradeLogRepository.save(TradeLog.builder()
                .member(member)
                .stock(stock)
                .quantity(request.quantity())
                .tradeDate(request.tradeDate())
                .profit(request.profit())
                .build()
        );
    }






}
