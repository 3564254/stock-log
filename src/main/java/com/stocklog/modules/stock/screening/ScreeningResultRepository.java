package com.stocklog.modules.stock.screening;

import com.stocklog.modules.common.type.Nation;
import com.stocklog.modules.stock.screening.entity.ScreeningCategory;
import com.stocklog.modules.stock.screening.entity.ScreeningResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScreeningResultRepository extends JpaRepository<ScreeningResult, Long> {

    boolean existsByNationAndCategoryIsNotNull(Nation nation);

    List<ScreeningResult> findAllByNation(Nation nation);

    List<ScreeningResult> findAllByCategory(ScreeningCategory category);

    /** 한 번에 지운다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ScreeningResult r where r.nation = :nation")
    int deleteAllByNation(@Param("nation") Nation nation);
}
