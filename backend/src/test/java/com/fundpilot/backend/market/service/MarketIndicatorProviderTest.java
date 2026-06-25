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
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #7 循环 E:{@code MarketIndicatorProvider} 从 snapshot 表读取单日指标,
 * 供 #12 SignalGenerationJob 使用。读不到时返回 empty,上层据此出 {@code INSUFFICIENT_MARKET_DATA}。
 */
class MarketIndicatorProviderTest extends AbstractIntegrationTest {

    @Autowired
    MarketIndicatorProvider marketIndicatorProvider;

    @Autowired
    MarketIndicatorSnapshotRepository snapshotRepository;

    @Autowired
    FundRepository fundRepository;

    @Test
    @Transactional
    void getIndicators_当日有快照_返回快照字段() {
        FundEntity fund = fundRepository.save(newFund("161725"));
        LocalDate date = LocalDate.of(2026, 6, 24);
        snapshotRepository.save(snapshot(fund, date, "1.2345"));

        Optional<MarketIndicatorSnapshotEntity> result = marketIndicatorProvider.getIndicators(fund.getId(), date);

        assertThat(result).isPresent();
        assertThat(result.get().getCurrentNav()).isEqualByComparingTo(new BigDecimal("1.2345"));
        assertThat(result.get().getWeeklyMacdState()).isEqualTo(WeeklyMacdState.GREEN_SHRINKING);
        assertThat(result.get().getVolumeState()).isEqualTo(VolumeState.NORMAL);
    }

    @Test
    @Transactional
    void getIndicators_当日无快照_返回_empty() {
        FundEntity fund = fundRepository.save(newFund("161726"));

        Optional<MarketIndicatorSnapshotEntity> result =
                marketIndicatorProvider.getIndicators(fund.getId(), LocalDate.of(2026, 6, 24));

        assertThat(result).isEmpty();
    }

    private FundEntity newFund(String code) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(code);
        fund.setFundName("测试基金-" + code);
        return fund;
    }

    private static MarketIndicatorSnapshotEntity snapshot(FundEntity fund, LocalDate date, String nav) {
        MarketIndicatorSnapshotEntity entity = new MarketIndicatorSnapshotEntity();
        entity.setFundEntity(fund);
        entity.setSnapshotDate(date);
        entity.setCurrentNav(new BigDecimal(nav));
        entity.setPriceAboveYearLine(true);
        entity.setYearLineRising(true);
        entity.setWeeklyMacdState(WeeklyMacdState.GREEN_SHRINKING);
        entity.setVolumeState(VolumeState.NORMAL);
        entity.setWeeklyDropPercent(new BigDecimal("0.01"));
        entity.setSixtyDayHigh(false);
        return entity;
    }
}
