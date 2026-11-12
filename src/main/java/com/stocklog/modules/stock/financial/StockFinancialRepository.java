package com.stocklog.modules.stock.financial;

import com.stocklog.modules.stock.domain.model.StockDateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface StockFinancialRepository extends JpaRepository<StockFinancial, StockDateId> {

    Optional<StockFinancial> findFirstByCodeOrderByBaseDateDesc(String code);

    /** 정상 행 중 가장 최근 */
    Optional<StockFinancial> findFirstByCodeAndBaseDateAfterOrderByBaseDateDesc(String code, java.time.LocalDate after);
}
