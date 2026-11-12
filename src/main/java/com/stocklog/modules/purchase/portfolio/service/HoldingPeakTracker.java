package com.stocklog.modules.purchase.portfolio.service;

import com.stocklog.modules.purchase.portfolio.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/** 보유 종목의 '매수 후 최고가'를 현재가로 갱신한다 */
@Service
@RequiredArgsConstructor
public class HoldingPeakTracker {

    private final HoldingRepository holdingRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void track(Map<Long, BigDecimal> currentPriceByHoldingId) {
        if (currentPriceByHoldingId == null || currentPriceByHoldingId.isEmpty()) return;
        holdingRepository.findAllById(currentPriceByHoldingId.keySet()).forEach(h -> {
            BigDecimal p = currentPriceByHoldingId.get(h.getId());
            if (p != null && p.signum() > 0) h.updateHighestPrice(p);   // 종가 기준 최고가를 받는다
        });
    }
}
