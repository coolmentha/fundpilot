package com.fundpilot.backend.market.repository;

import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketIndicatorSnapshotRepository
        extends JpaRepository<MarketIndicatorSnapshotEntity, Long> {
}
