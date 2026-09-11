package com.stocklog.modules.market.exchange.repository;

import com.stocklog.modules.market.exchange.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    default Optional<ExchangeRate> findMainRate() {
        return findById(1L);
    }
}
