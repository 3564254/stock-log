package com.stocklog.modules.stock.signal;

import com.stocklog.modules.common.type.Nation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SignalRecordRepository extends JpaRepository<SignalRecord, Long> {
    boolean existsByStockCodeAndSignalDateAndEngineVersion(String stockCode, LocalDate signalDate, String engineVersion);
    List<SignalRecord> findTop500ByOutcomeStatusInAndSignalDateBeforeOrderBySignalDateAsc(Collection<String> statuses, LocalDate before);
    List<SignalRecord> findByNationAndEngineVersionAndTrackVersionOrderByStockCodeAscSignalDateAsc(Nation nation, String engineVersion, String trackVersion);
    long countByNationAndEngineVersion(Nation nation, String engineVersion);
    boolean existsByNationAndEngineVersionAndRecordedAtAfter(Nation nation, String engineVersion, java.time.LocalDateTime after);
}
