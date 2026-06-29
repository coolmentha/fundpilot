package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.repository.MarketIndicatorSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 行情指标快照落库服务——封装「同日重跑覆盖」的幂等 upsert 语义。
 * <p>{@link MarketDataFetchJob} 每只基金算完指标后调 {@link #upsert(MarketIndicatorSnapshotEntity)},
 * 同一 (fund_id, snapshot_date) 已存在则覆盖字段(重跑幂等),不存在则新建。
 */
@Service
@RequiredArgsConstructor
public class MarketIndicatorSnapshotService {

    private final MarketIndicatorSnapshotRepository snapshotRepository;

    @Transactional
    public MarketIndicatorSnapshotEntity upsert(MarketIndicatorSnapshotEntity template) {
        Long fundId = template.getFundEntity().getId();
        Optional<MarketIndicatorSnapshotEntity> existing =
                snapshotRepository.findByFundEntity_IdAndSnapshotDate(fundId, template.getSnapshotDate());
        if (existing.isPresent()) {
            MarketIndicatorSnapshotEntity entity = existing.get();
            entity.setCurrentNav(template.getCurrentNav());
            entity.setPriceAboveYearLine(template.isPriceAboveYearLine());
            entity.setYearLineRising(template.isYearLineRising());
            entity.setWeeklyMacdState(template.getWeeklyMacdState());
            entity.setVolumeState(template.getVolumeState());
            entity.setWeeklyDropPercent(template.getWeeklyDropPercent());
            entity.setSixtyDayHigh(template.isSixtyDayHigh());
            return snapshotRepository.save(entity);
        }
        return snapshotRepository.save(template);
    }
}
