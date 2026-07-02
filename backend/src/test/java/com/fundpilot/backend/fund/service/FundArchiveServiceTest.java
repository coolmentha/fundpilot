package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundStrategyActivationEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * issue #1 归档端点回归:DELETE /api/funds/{id} 软删基金 + 级联软删关联数据。
 * <p>PRD §归档:软删除=归档,与 FundStatus 正交,关联数据一起软删。
 */
@Transactional
class FundArchiveServiceTest extends AbstractIntegrationTest {

    @Autowired FundArchiveService fundArchiveService;
    @Autowired EntityManager entityManager;

    @Test
    void archive_基金不存在_抛BusinessException() {
        assertThatThrownBy(() -> fundArchiveService.archive(999_999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Fund #999999 不存在");
    }

    @Test
    void archive_软删自身及全部关联数据() {
        FundEntity fund = persistFund();
        entityManager.flush();
        FundStrategyEntity strategy = persistStrategy(fund);
        persistActivation(fund, strategy);
        persistTransaction(fund);
        persistSignalLog(fund, strategy);
        persistNavHistory(fund);
        persistMarketSnapshot(fund);
        entityManager.flush();
        Long fundId = fund.getId();

        fundArchiveService.archive(fundId);
        entityManager.flush();
        entityManager.clear();

        // 自身:默认查询(@SQLRestriction)查不到
        assertThat(entityManager.find(FundEntity.class, fundId)).isNull();
        // 六张关联表:deleted_date 已写(用原生 SQL 绕过 @SQLRestriction 验证)
        // fund 表主键列是 id;关联表外键列是 fund_id
        assertThat(deletedCount("fund", "id", fundId)).isEqualTo(1);
        assertThat(deletedCount("fund_strategy", "fund_id", fundId)).isEqualTo(1);
        assertThat(deletedCount("fund_strategy_activation", "fund_id", fundId)).isEqualTo(1);
        assertThat(deletedCount("fund_transaction", "fund_id", fundId)).isEqualTo(1);
        assertThat(deletedCount("signal_log", "fund_id", fundId)).isEqualTo(1);
        assertThat(deletedCount("fund_nav_history", "fund_id", fundId)).isEqualTo(1);
        assertThat(deletedCount("market_indicator_snapshot", "fund_id", fundId)).isEqualTo(1);
    }

    /** 用原生 SQL 绕过 @SQLRestriction,数 deleted_date 非空的行数。 */
    private int deletedCount(String table, String idColumn, Long fundId) {
        Long count = (Long) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = :fid AND deleted_date IS NOT NULL")
                .setParameter("fid", fundId)
                .getSingleResult();
        return count.intValue();
    }

    private FundEntity persistFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setStatus(FundStatus.HOLDING);
        fund.setDcaAmount(new BigDecimal("100000"));
        entityManager.persist(fund);
        return fund;
    }

    private FundStrategyEntity persistStrategy(FundEntity fund) {
        FundStrategyEntity s = new FundStrategyEntity();
        s.setFundEntity(fund);
        s.setStatus(StrategyParamStatus.EFFECTIVE);
        // 移动止盈参数(ADR-0015):归档测试只验证级联软删,参数值用宽基默认
        s.setActivationThreshold(new BigDecimal("0.50"));
        s.setPullbackTierCount(3);
        s.setPullbackTier1Yield(new BigDecimal("0.50"));
        s.setPullbackTier1Ratio(new BigDecimal("0.15"));
        s.setPullbackTier2Yield(new BigDecimal("0.80"));
        s.setPullbackTier2Ratio(new BigDecimal("0.18"));
        s.setPullbackTier3Yield(new BigDecimal("1.50"));
        s.setPullbackTier3Ratio(new BigDecimal("0.20"));
        s.setSellRatio(new BigDecimal("0.20"));
        s.setFloorRatio(new BigDecimal("0.40"));
        s.setCooldownDays(20);
        entityManager.persist(s);
        return s;
    }

    private void persistActivation(FundEntity fund, FundStrategyEntity strategy) {
        FundStrategyActivationEntity a = new FundStrategyActivationEntity();
        a.setFundEntity(fund);
        a.setFundStrategyEntity(strategy);
        a.setActivatedAt(Instant.now());
        entityManager.persist(a);
    }

    private void persistTransaction(FundEntity fund) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.INCREASE);
        tx.setAmount(new BigDecimal("1000"));
        tx.setShares(new BigDecimal("800"));
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        entityManager.persist(tx);
    }

    private void persistSignalLog(FundEntity fund, FundStrategyEntity strategy) {
        SignalLogEntity log = new SignalLogEntity();
        log.setFundEntity(fund);
        log.setFundStrategyEntity(strategy);
        log.setSignalDate(Instant.now());
        entityManager.persist(log);
    }

    private void persistNavHistory(FundEntity fund) {
        FundNavHistoryEntity nav = new FundNavHistoryEntity();
        nav.setFundEntity(fund);
        nav.setNavDate(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant());
        nav.setNav(new BigDecimal("1.25"));
        nav.setAccumulatedNav(new BigDecimal("1.25"));
        entityManager.persist(nav);
    }

    private void persistMarketSnapshot(FundEntity fund) {
        MarketIndicatorSnapshotEntity snap = new MarketIndicatorSnapshotEntity();
        snap.setFundEntity(fund);
        snap.setSnapshotDate(LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant());
        snap.setCurrentNav(new BigDecimal("1.25"));
        entityManager.persist(snap);
    }
}
