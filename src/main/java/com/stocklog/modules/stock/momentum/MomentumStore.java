package com.stocklog.modules.stock.momentum;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 하루치 순위를 통째로 바꾼다 */
@Component
@RequiredArgsConstructor
class MomentumStore {
    private final MomentumRankRepository repository;

    @Transactional
    public void replace(LocalDate asOf, List<MomentumRank> rows) {
        repository.deleteByAsOf(asOf);
        repository.saveAll(rows);
        repository.deleteOlderThan(asOf.minusDays(400));
    }
}
