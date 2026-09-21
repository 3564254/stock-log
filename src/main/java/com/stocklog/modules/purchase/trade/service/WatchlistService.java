package com.stocklog.modules.purchase.trade.service;

import com.stocklog.modules.member.application.usecase.GetMemberUseCase;
import com.stocklog.modules.member.entity.Member;
import com.stocklog.modules.purchase.trade.dto.vo.WatchItemDto;
import com.stocklog.modules.purchase.trade.entity.WatchItem;
import com.stocklog.modules.purchase.trade.repository.WatchItemRepository;
import com.stocklog.modules.stock.analysis.domain.signal.vo.StockPhase;
import com.stocklog.modules.stock.application.usecase.GetStockInfoUseCase;
import com.stocklog.modules.stock.info.StockInfo;
import com.stocklog.global.error.BusinessException;
import com.stocklog.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true) // 기본은 읽기 전용
@RequiredArgsConstructor
public class WatchlistService { // default 접근 제어자 유지 (Facade에서 호출)

    private final WatchItemRepository watchItemRepository;
    private final GetStockInfoUseCase stockInfoUseCase;
    private final GetMemberUseCase memberUsecase;

    @Transactional
    public void addWatchList(Long memberId, String stockCode) {
        if (watchItemRepository.existsByMemberIdAndStockCode(memberId, stockCode)) {
            throw new BusinessException(ErrorCode.DUPLICATE_WATCH_ITEM);
        }
        Member memberProxy = memberUsecase.getMemberReference(memberId);
        StockInfo stockInfoProxy = stockInfoUseCase.getStockInfo(stockCode);
        watchItemRepository.save(WatchItem.builder().stock(stockInfoProxy).member(memberProxy).build());
    }

    // 💡 목록 조회: DB 값만 꺼내서 바로 DTO로 변환 (O(1) 초고속)
    public List<WatchItemDto> getWatchlist(Long memberId) {
        return watchItemRepository.findAllByMemberId(memberId).stream()
                .map(WatchItemDto::of)
                .toList();
    }

    // 💡 단건 조회: 기존의 무거운 getWatchItemDto() 대신 바로 WatchItemDto::of 사용
    public WatchItemDto getWatchlistItem(Long memberId, String stockCode) {
        return watchItemRepository.findByMemberIdAndStockCode(memberId, stockCode)
                .map(WatchItemDto::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.WATCH_ITEM_NOT_FOUND));
    }

    @Transactional
    void deleteWatchlistItem(Long memberId, String stockCode) {
        watchItemRepository.deleteByMemberIdAndStockCode(memberId, stockCode);
    }

    @Transactional
    public void updateAnalyzedSignal(Long memberId, String stockCode, StockPhase phase, Double targetPrice) {
        WatchItem watchItem = watchItemRepository.findByMemberIdAndStockCode(memberId, stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.WATCH_ITEM_NOT_FOUND));

        watchItem.updateAnalyzedSignal(phase, targetPrice);
    }
}