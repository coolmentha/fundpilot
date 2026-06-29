package com.fundpilot.backend.market.entity;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import com.fundpilot.backend.market.repository.MarketIndicatorSnapshotRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #3 验收证据:market_indicator_snapshot 表 + MarketIndicatorSnapshotEntity + Repository。
 * <p>每日 14:50 行情指标快照(表级缓存):MarketDataFetchJob 写入,SignalGenerationJob 读取,
 * 不再发外部请求。每只基金每日一行(按 (fund_id, snapshot_date) 唯一,见 cycle 5)。
 * 字段集与 issue #3 AC 一致:current_nav / price_above_year_line / year_line_rising /
 * weekly_macd_state / volume_state / weekly_drop_percent / is_sixty_day_high。
 */
class MarketIndicatorSnapshotSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    MarketIndicatorSnapshotRepository marketIndicatorSnapshotRepository;

    @Autowired
    FundRepository fundRepository;

    @Test
    @Transactional
    void marketIndicatorSnapshotPersistsAllIndicatorFields() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fundRepository.save(fund);

        MarketIndicatorSnapshotEntity snapshot = new MarketIndicatorSnapshotEntity();
        snapshot.setFundEntity(fund);
        snapshot.setSnapshotDate(Instant.parse("2026-06-24T00:00:00Z"));
        snapshot.setCurrentNav(new BigDecimal("4.12345678"));
        snapshot.setPriceAboveYearLine(true);
        snapshot.setYearLineRising(true);
        snapshot.setWeeklyMacdState(WeeklyMacdState.GREEN_EXPANDING);
        snapshot.setVolumeState(VolumeState.HIGH_DROP);
        snapshot.setWeeklyDropPercent(new BigDecimal("0.03250000"));
        snapshot.setSixtyDayHigh(true);

        MarketIndicatorSnapshotEntity saved = marketIndicatorSnapshotRepository.save(snapshot);
        entityManager.flush();
        entityManager.clear();

        MarketIndicatorSnapshotEntity reloaded =
                marketIndicatorSnapshotRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getFundEntity().getId()).isEqualTo(fund.getId());
        assertThat(reloaded.getSnapshotDate()).isEqualTo(Instant.parse("2026-06-24T00:00:00Z"));
        assertThat(reloaded.getCurrentNav()).isEqualByComparingTo(new BigDecimal("4.12345678"));
        assertThat(reloaded.isPriceAboveYearLine()).isTrue();
        assertThat(reloaded.isYearLineRising()).isTrue();
        assertThat(reloaded.getWeeklyMacdState()).isEqualTo(WeeklyMacdState.GREEN_EXPANDING);
        assertThat(reloaded.getVolumeState()).isEqualTo(VolumeState.HIGH_DROP);
        assertThat(reloaded.getWeeklyDropPercent()).isEqualByComparingTo(new BigDecimal("0.0325"));
        assertThat(reloaded.isSixtyDayHigh()).isTrue();
    }
}
