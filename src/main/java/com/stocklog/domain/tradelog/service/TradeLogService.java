package com.stocklog.domain.tradelog.service;

import com.stocklog.domain.member.entity.Member;
import com.stocklog.domain.member.service.MemberService;
import com.stocklog.domain.application.StockFacadeService;
import com.stocklog.domain.stock.info.dto.internal.StockInfoDto;
import com.stocklog.domain.tradelog.dto.request.TradeLogRequest;
import com.stocklog.domain.tradelog.dto.response.TradeLogResponse;
import com.stocklog.domain.tradelog.entity.TradeLog;
import com.stocklog.domain.tradelog.repository.TradeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TradeLogService {
    private final TradeLogRepository tradeLogRepository;
    private final MemberService memberService;
    private final StockFacadeService stockFacadeService;

    public TradeLogResponse getMonthlyTradeLogs(Long memberId, LocalDate targetDate, SortType type) {
        Member member = memberService.findById(memberId);

        LocalDate startDate = targetDate.withDayOfMonth(1);
        LocalDate endDate = startDate.plusMonths(1);

        Sort sort = SortType.getSort(type);

        List<TradeLog> tradeLogs = tradeLogRepository.findMonthlyTradeLogs(member, startDate, endDate, sort);
        return TradeLogResponse.of(targetDate, tradeLogs);
    }

    @Transactional
    public void saveTradeLog(Long memberId, TradeLogRequest request) {
        Member member = memberService.findById(memberId);
        StockInfoDto stock = stockFacadeService.getInfo(request.stockCode());

        tradeLogRepository.save(TradeLog.builder()
                .member(member)
                .stock(StockInfoDto.toEntity(stock))
                .quantity(request.quantity())
                .tradeDate(request.tradeDate())
                .profit(request.profit())
                .build()
        );
    }

    public enum SortType {
        DATE_DEC,
        DATE_ASC,
        PROFIT;

        public static Sort getSort(SortType type) {
            return switch (type) {
                case DATE_ASC -> Sort.by("tradeDate").ascending();
                case PROFIT -> Sort.by("realizedProfit").descending();
                default -> Sort.by("tradeDate").descending();
            };
        }
        public static Sort getSort() {
            return Sort.by("tradeDate").descending();
        }

    }






}
