package com.stocklog.modules.stock.momentum;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MomentumRankRepository extends JpaRepository<MomentumRank, Long> {

    @Query("SELECT MAX(m.asOf) FROM MomentumRank m")
    LocalDate findLatestAsOf();

    List<MomentumRank> findByAsOfOrderByRankNoAsc(LocalDate asOf);

    Optional<MomentumRank> findFirstByAsOfAndCode(LocalDate asOf, String code);

    @Modifying
    @Query("DELETE FROM MomentumRank m WHERE m.asOf = :asOf")
    void deleteByAsOf(LocalDate asOf);

    @Modifying
    @Query("DELETE FROM MomentumRank m WHERE m.asOf < :before")
    void deleteOlderThan(LocalDate before);
}
