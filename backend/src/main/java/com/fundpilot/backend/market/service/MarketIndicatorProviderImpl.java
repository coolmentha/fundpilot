package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.repository.MarketIndicatorSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * {@link MarketIndicatorProvider} 默认实现:直接从 snapshot 表按 (fund_id, snapshot_date) 读取。
 * 软删行由 {@code @SQLRestriction} 自动过滤。
 */
@Service
@RequiredArgsConstructor
public class MarketIndicatorProviderImpl implements MarketIndicatorProvider {

    private final MarketIndicatorSnapshotRepository snapshotRepository;

    @Override
    public Optional<MarketIndicatorSnapshotEntity> getIndicators(Long fundId, Instant date) {
        return snapshotRepository.findByFundEntity_IdAndSnapshotDate(fundId, date);
    }
}
