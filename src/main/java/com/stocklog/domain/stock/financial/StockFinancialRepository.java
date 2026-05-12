package com.stocklog.domain.stock.financial;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockFinancialRepository extends JpaRepository<StockFinancial, String> {
}
