package com.stocklog.modules.stock.valutation;

import com.stocklog.modules.stock.domain.model.StockDateId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockValuationRepository extends JpaRepository<StockValuation, StockDateId> {
    Optional<StockValuation> findFirstByCodeOrderByBaseDateDesc(String code);
}
