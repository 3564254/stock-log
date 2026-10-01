package com.stocklog.modules.market.diagnosis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketDiagnosisRepository extends JpaRepository<MarketDiagnosis, Long> {

    Optional<MarketDiagnosis> findByDate(LocalDate date);

    Optional<MarketDiagnosis> findTopByOrderByDateDesc();

    @Query(value = "SELECT * FROM market_diagnosis ORDER BY date DESC LIMIT :n ", nativeQuery = true)
    List<MarketDiagnosis> findDiagnosisDESCTopN(int n);

}
