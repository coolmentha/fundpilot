package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NavConfirmService + TransactionCancelService 集成测试(issue #15)。
 * @SpringBootTest + 真实 PostgreSQL(项目约定,@DataJpaTest 关 Flyway 导致 validate 失败)。
 */
@Transactional
class NavConfirmAndCancelServiceTest extends AbstractIntegrationTest {

    @Autowired NavConfirmService navConfirmService;
    @Autowired TransactionCancelService transactionCancelService;
    @Autowired EntityManager entityManager;

    private FundEntity fund;
    private Instant today;

    @BeforeEach
    void setUp() {
        today = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setStatus(FundStatus.HOLDING);
        fund.setPlannedTotalAmount(new BigDecimal("100000"));
        entityManager.persist(fund);
    }

    private FundTransactionEntity persistTx(FundTransactionSource source, BigDecimal amount, BigDecimal shares) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(source);
        tx.setAmount(amount);
        tx.setShares(shares);
        tx.setStatus(FundTransactionStatus.PENDING);
        entityManager.persist(tx);
        return tx;
    }

    private void persistNavToday(BigDecimal accumulatedNav) {
        FundNavHistoryEntity nav = new FundNavHistoryEntity();
        nav.setFundEntity(fund);
        nav.setNavDate(today);
        nav.setNav(accumulatedNav);
        nav.setAccumulatedNav(accumulatedNav);
        entityManager.persist(nav);
    }

    @Test
    void confirmPendingTransactions_买入PENDING回填shares转CONFIRMED() {
        persistNavToday(new BigDecimal("1.25"));
        FundTransactionEntity tx = persistTx(FundTransactionSource.INCREASE, new BigDecimal("1000"), null);
        entityManager.flush();

        int confirmed = navConfirmService.confirmPendingTransactions(today);

        assertThat(confirmed).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();
        FundTransactionEntity reloaded = entityManager.find(FundTransactionEntity.class, tx.getId());
        assertThat(reloaded.getStatus()).isEqualTo(FundTransactionStatus.CONFIRMED);
        assertThat(reloaded.getNav()).isEqualByComparingTo("1.25");
        assertThat(reloaded.getShares()).isEqualByComparingTo("800"); // 1000 / 1.25
        assertThat(reloaded.getConfirmTime()).isNotNull();
    }

    @Test
    void confirmPendingTransactions_卖出PENDING回填amount转CONFIRMED() {
        persistNavToday(new BigDecimal("2.00"));
        FundTransactionEntity tx = persistTx(FundTransactionSource.DECREASE, null, new BigDecimal("500"));
        entityManager.flush();

        navConfirmService.confirmPendingTransactions(today);

        entityManager.flush();
        entityManager.clear();
        FundTransactionEntity reloaded = entityManager.find(FundTransactionEntity.class, tx.getId());
        assertThat(reloaded.getStatus()).isEqualTo(FundTransactionStatus.CONFIRMED);
        assertThat(reloaded.getNav()).isEqualByComparingTo("2.00");
        assertThat(reloaded.getAmount()).isEqualByComparingTo("1000"); // 500 × 2.00
    }

    @Test
    void confirmPendingTransactions_当日无净值行的PENDING不动() {
        // 不 persistNavToday —— 当日无净值
        FundTransactionEntity tx = persistTx(FundTransactionSource.INCREASE, new BigDecimal("1000"), null);
        entityManager.flush();

        int confirmed = navConfirmService.confirmPendingTransactions(today);

        assertThat(confirmed).isEqualTo(0);
        entityManager.flush();
        entityManager.clear();
        FundTransactionEntity reloaded = entityManager.find(FundTransactionEntity.class, tx.getId());
        assertThat(reloaded.getStatus()).isEqualTo(FundTransactionStatus.PENDING); // 保留
        assertThat(reloaded.getNav()).isNull();
    }

    @Test
    void cancel_PENDING转CANCELLED() {
        FundTransactionEntity tx = persistTx(FundTransactionSource.INCREASE, new BigDecimal("1000"), null);
        entityManager.flush();

        List<FundTransactionEntity> cancelled = transactionCancelService.cancel(tx.getId());

        assertThat(cancelled).hasSize(1);
        assertThat(cancelled.get(0).getStatus()).isEqualTo(FundTransactionStatus.CANCELLED);
        assertThat(cancelled.get(0).getCancelTime()).isNotNull();
    }

    @Test
    void cancel_CONFIRMED抛TRANSACTION_ALREADY_CONFIRMED() {
        FundTransactionEntity tx = persistTx(FundTransactionSource.INCREASE, new BigDecimal("1000"), null);
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        entityManager.flush();

        assertThatThrownBy(() -> transactionCancelService.cancel(tx.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已确认");
    }

    @Test
    void cancel_转换交易两条腿一起CANCELLED() {
        FundEntity fundB = new FundEntity();
        fundB.setFundCode("161725");
        fundB.setFundName("招商白酒");
        fundB.setFundCategory(FundCategory.SECTOR);
        fundB.setStatus(FundStatus.HOLDING);
        entityManager.persist(fundB);

        // fund → fundB 转出
        FundTransactionEntity out = persistTx(FundTransactionSource.TRANSFER_OUT, null, new BigDecimal("100"));
        // fundB ← fund 转入
        FundTransactionEntity in = new FundTransactionEntity();
        in.setFundEntity(fundB);
        in.setSource(FundTransactionSource.TRANSFER_IN);
        in.setShares(new BigDecimal("100"));
        in.setStatus(FundTransactionStatus.PENDING);
        entityManager.persist(in);
        out.setRelatedFundTransactionEntity(in);
        in.setRelatedFundTransactionEntity(out);
        entityManager.flush();

        List<FundTransactionEntity> cancelled = transactionCancelService.cancel(out.getId());

        assertThat(cancelled).hasSize(2);
        assertThat(cancelled).allMatch(t -> t.getStatus() == FundTransactionStatus.CANCELLED);
    }

    @Test
    void cancel_交易不存在抛BusinessException() {
        assertThatThrownBy(() -> transactionCancelService.cancel(999999L))
                .isInstanceOf(BusinessException.class);
    }
}
