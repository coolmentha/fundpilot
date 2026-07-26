package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.marketdata.adapter.api.indicator.MarketIndicatorApi;
import com.fundpilot.backend.market.repository.MarketIndicatorSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 行情指标快照落库服务——封装「同日重跑覆盖」的幂等 upsert 语义。
 * <p>{@link MarketDataFetchJob} 每只基金算完指标后调 {@link #upsert(MarketIndicatorSnapshotEntity)},
 * 同一 (fund_id, snapshot_date) 已存在则覆盖字段(重跑幂等),不存在则新建。
 */
@Service
@RequiredArgsConstructor
public class MarketIndicatorSnapshotService {

    private final MarketIndicatorApi marketIndicatorApi;
    private final MarketIndicatorSnapshotRepository legacySnapshotRepository;

    @Transactional
    public MarketIndicatorSnapshotEntity upsert(MarketIndicatorSnapshotEntity template) {
        Long fundId = template.getFundEntity().getId();
        template.setFundCode(template.getFundEntity().getFundCode());
        Long productId = template.getFundEntity().getProductId();
        if (productId == null) {
            return legacySnapshotRepository.findByFundEntity_IdAndSnapshotDate(fundId, template.getSnapshotDate())
                    .map(existing -> {
                        existing.setCurrentNav(template.getCurrentNav());
                        existing.setPriceAboveYearLine(template.isPriceAboveYearLine());
                        existing.setYearLineRising(template.isYearLineRising());
                        existing.setWeeklyMacdState(template.getWeeklyMacdState());
                        existing.setVolumeState(template.getVolumeState());
                        existing.setWeeklyDropPercent(template.getWeeklyDropPercent());
                        existing.setSixtyDayHigh(template.isSixtyDayHigh());
                        return legacySnapshotRepository.save(existing);
                    }).orElseGet(() -> legacySnapshotRepository.save(template));
        }
        marketIndicatorApi.upsert(new MarketIndicatorApi.Upsert(fundId, productId,
                template.getFundCode(), template.getSnapshotDate(), template.getCurrentNav(),
                template.isPriceAboveYearLine(), template.isYearLineRising(),
                template.getWeeklyMacdState() == null ? null : template.getWeeklyMacdState().name(),
                template.getVolumeState() == null ? null : template.getVolumeState().name(),
                template.getWeeklyDropPercent(), template.isSixtyDayHigh()));
        return template;
    }
}
