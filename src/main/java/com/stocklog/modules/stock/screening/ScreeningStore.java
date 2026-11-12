package com.stocklog.modules.stock.screening;

import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.screening.entity.ScreeningResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 스크리닝 결과를 국가별로 한 트랜잭션에서 교체한다 */
@Component
@RequiredArgsConstructor
class ScreeningStore {

    private final ScreeningResultRepository repository;

    @Transactional
    public void replace(Nation nation, List<ScreeningResult> results) {
        repository.deleteAllByNation(nation);
        repository.saveAll(results);
    }
}
