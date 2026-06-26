package com.fundpilot.backend.market.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import com.fundpilot.backend.market.repository.MarketIndicatorSnapshotRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #7 循环 D:{@code MarketIndicatorSnapshotService.upsert} 的「同日重跑覆盖」幂等语义。
 * <p>重跑场景:定时任务 14:30 跑完后 14:40 又被人工触发,或同日多次 {@code POST /refresh},
 * 同一 (fund_id, snapshot_date) 不应产生多行,而是字段被覆盖更新。
 */
class MarketIndicatorSnapshotServiceTest extends AbstractIntegrationTest {

    @Autowired
    MarketIndicatorSnapshotService marketIndicatorSnapshotService;

    @Autowired
    MarketIndicatorSnapshotRepository marketIndicatorSnapshotRepository;

    @Autowired
    FundRepository fundRepository;

    @Test
    @Transactional
    void upsert_首次写入_落库一行字段正确() {
        FundEntity fund = persistFund("161725");
        Instant date = Instant.parse("2026-06-24T00:00:00Z");
        MarketIndicatorSnapshotEntity template = template(fund, date, "1.2345", true);

        MarketIndicatorSnapshotEntity saved = marketIndicatorSnapshotService.upsert(template);

        assertThat(saved.getId()).isNotNull();
        assertThat(marketIndicatorSnapshotRepository.count()).isEqualTo(1L);
        assertThat(saved.getCurrentNav()).isEqualByComparingTo(new BigDecimal("1.2345"));
        assertThat(saved.isPriceAboveYearLine()).isTrue();
        assertThat(saved.getWeeklyMacdState()).isEqualTo(WeeklyMacdState.GREEN_SHRINKING);
    }

    @Test
    @Transactional
    void upsert_同日重跑_覆盖字段不新增行() {
        FundEntity fund = persistFund("161726");
        Instant date = Instant.parse("2026-06-24T00:00:00Z");
        marketIndicatorSnapshotService.upsert(template(fund, date, "1.0000", false));

        // 同日重跑,字段全部变化
        MarketIndicatorSnapshotEntity updated = marketIndicatorSnapshotService.upsert(template(fund, date, "2.5000", true));

        assertThat(marketIndicatorSnapshotRepository.count()).isEqualTo(1L);
        assertThat(updated.getCurrentNav()).isEqualByComparingTo(new BigDecimal("2.5000"));
        assertThat(updated.isPriceAboveYearLine()).isTrue();
    }

    @Test
    @Transactional
    void findByFundIdAndSnapshotDate_不存在时返回_empty() {
        FundEntity fund = persistFund("161727");

        Optional<MarketIndicatorSnapshotEntity> found =
                marketIndicatorSnapshotRepository.findByFundEntity_IdAndSnapshotDate(fund.getId(), Instant.parse("2026-06-24T00:00:00Z"));

        assertThat(found).isEmpty();
    }

    private FundEntity persistFund(String code) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(code);
        fund.setFundName("测试基金-" + code);
        return fundRepository.save(fund);
    }

    private static MarketIndicatorSnapshotEntity template(FundEntity fund, Instant date, String nav, boolean aboveYearLine) {
        MarketIndicatorSnapshotEntity entity = new MarketIndicatorSnapshotEntity();
        entity.setFundEntity(fund);
        entity.setSnapshotDate(date);
        entity.setCurrentNav(new BigDecimal(nav));
        entity.setPriceAboveYearLine(aboveYearLine);
        entity.setYearLineRising(true);
        entity.setWeeklyMacdState(WeeklyMacdState.GREEN_SHRINKING);
        entity.setVolumeState(VolumeState.NORMAL);
        entity.setWeeklyDropPercent(new BigDecimal("0.0123"));
        entity.setSixtyDayHigh(false);
        return entity;
    }
}
