package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import com.fundpilot.backend.marketdata.adapter.api.indicator.MarketIndicatorApi;
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

    private final FundRepository fundRepository;
    private final MarketIndicatorApi marketIndicatorApi;
    private final MarketIndicatorSnapshotRepository legacySnapshotRepository;

    @Override
    public Optional<MarketIndicatorSnapshotEntity> getIndicators(Long fundId, Instant date) {
        var fund = fundRepository.findById(fundId);
        if (fund.isEmpty()) return Optional.empty();
        if (fund.orElseThrow().getProductId() == null) {
            return legacySnapshotRepository.findByFundEntity_IdAndSnapshotDate(fundId, date);
        }
        return marketIndicatorApi.find(fund.orElseThrow().getProductId(), date)
                .map(this::toLegacy);
    }

    private MarketIndicatorSnapshotEntity toLegacy(MarketIndicatorApi.Snapshot snapshot) {
        MarketIndicatorSnapshotEntity entity = new MarketIndicatorSnapshotEntity();
        entity.setFundCode(snapshot.fundCode());
        entity.setSnapshotDate(snapshot.snapshotDate());
        entity.setCurrentNav(snapshot.currentNav());
        entity.setPriceAboveYearLine(snapshot.priceAboveYearLine());
        entity.setYearLineRising(snapshot.yearLineRising());
        entity.setWeeklyMacdState(snapshot.weeklyMacdState() == null ? null
                : WeeklyMacdState.valueOf(snapshot.weeklyMacdState()));
        entity.setVolumeState(snapshot.volumeState() == null ? null
                : VolumeState.valueOf(snapshot.volumeState()));
        entity.setWeeklyDropPercent(snapshot.weeklyDropPercent());
        entity.setSixtyDayHigh(snapshot.sixtyDayHigh());
        return entity;
    }
}
