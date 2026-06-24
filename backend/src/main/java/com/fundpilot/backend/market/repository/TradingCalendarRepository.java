package com.fundpilot.backend.market.repository;

import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingCalendarRepository extends JpaRepository<TradingCalendarEntity, Long> {
}
