package com.fundpilot.backend.market.entity;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.repository.MarketIndicatorSnapshotRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * issue #3 验收:(fund_id, snapshot_date) 唯一约束。
 * <p>每只基金每日只允许一行快照,SignalGenerationJob 据此保证"一只基金每日一行"的读取语义。
 * 用部分唯一索引 {@code WHERE deleted_date IS NULL} 兜底,软删行不占唯一名额。
 */
class MarketIndicatorSnapshotUniqueConstraintTest extends AbstractIntegrationTest {

    @Autowired
    MarketIndicatorSnapshotRepository marketIndicatorSnapshotRepository;

    @Autowired
    FundRepository fundRepository;

    @Test
    @Transactional
    void duplicateFundAndSnapshotDateIsRejected() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("161725");
        fund.setFundName("招商中证白酒指数");
        fundRepository.save(fund);

        LocalDate date = LocalDate.of(2026, 6, 24);

        MarketIndicatorSnapshotEntity first = new MarketIndicatorSnapshotEntity();
        first.setFundEntity(fund);
        first.setSnapshotDate(date);
        first.setCurrentNav(new BigDecimal("1.00000000"));
        marketIndicatorSnapshotRepository.saveAndFlush(first);

        MarketIndicatorSnapshotEntity second = new MarketIndicatorSnapshotEntity();
        second.setFundEntity(fund);
        second.setSnapshotDate(date);
        second.setCurrentNav(new BigDecimal("1.10000000"));

        assertThatThrownBy(() -> marketIndicatorSnapshotRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
