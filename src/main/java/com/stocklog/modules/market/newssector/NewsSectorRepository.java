package com.stocklog.modules.market.newssector;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface NewsSectorRepository extends JpaRepository<NewsSectorEntity, Long> {
    Optional<NewsSectorEntity> findByDay(LocalDate day);
    Optional<NewsSectorEntity> findTopByOrderByDayDesc();
}
