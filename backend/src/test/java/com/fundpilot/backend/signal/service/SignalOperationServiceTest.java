package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.EntityNotFoundException;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.signal.controller.ConfirmOperationRequest;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SignalOperationService 集成测试(issue #14):@SpringBootTest + 真实 PostgreSQL,
 * 验证 5 类信号确认分派 + 写 FundTransaction + 推进 tierNAddedAt/FundStatus。
 * <p>用 @SpringBootTest 而非 @DataJpaTest:项目约定(@DataJpaTest 关 Flyway 导致 validate 失败,见 AbstractIntegrationTest)。
 */
@Transactional
class SignalOperationServiceTest extends AbstractIntegrationTest {

    @Autowired SignalOperationService service;
    @Autowired SignalLogRepository signalLogRepository;
    @Autowired FundTransactionRepository fundTransactionRepository;
    @Autowired FundPositionService fundPositionService;
    @Autowired EntityManager entityManager;

    private FundEntity fund;
    private FundStrategyEntity strategy;

    @BeforeEach
    void setUp() {
        fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setStatus(FundStatus.PENDING_HOLDING);
        fund.setPlannedTotalAmount(new BigDecimal("100000"));
        entityManager.persist(fund);

        strategy = new FundStrategyEntity();
        strategy.setFundEntity(fund);
        strategy.setStatus(StrategyParamStatus.EFFECTIVE);
        strategy.setTier1Drawdown(new BigDecimal("-0.08"));
        strategy.setTier2Drawdown(new BigDecimal("-0.16"));
        strategy.setTier3Drawdown(new BigDecimal("-0.25"));
        strategy.setTier4Drawdown(new BigDecimal("-0.35"));
        strategy.setTier1Ratio(new BigDecimal("0.30"));
        strategy.setTier2Ratio(new BigDecimal("0.30"));
        strategy.setTier3Ratio(new BigDecimal("0.20"));
        strategy.setTier4Ratio(new BigDecimal("0.20"));
        entityManager.persist(strategy);
    }

    private SignalLogEntity persistSignal(SignalType type, Integer tier, String reason) {
        SignalLogEntity log = new SignalLogEntity();
        log.setFundEntity(fund);
        log.setFundStrategyEntity(strategy);
        log.setSignalDate(Instant.now());
        log.setSignalType(type);
        log.setTriggerTier(tier);
        log.setReason(reason);
        entityManager.persist(log);
        return log;
    }

    @Test
    void confirmOperation_BUILD推进HOLDING并写openedAt和INCREASE交易() {
        SignalLogEntity signal = persistSignal(SignalType.BUILD, null, "BUILD_TRIGGERED");
        entityManager.flush();

        FundTransactionEntity tx = service.confirmOperation(signal.getId(),
                new ConfirmOperationRequest(signal.getId(), new BigDecimal("10000"), null));

        assertThat(tx.getSource()).isEqualTo(FundTransactionSource.INCREASE);
        assertThat(tx.getAmount()).isEqualByComparingTo("10000");
        assertThat(tx.getShares()).isNull();
        assertThat(tx.getNav()).isNull();
        assertThat(tx.getStatus()).isEqualTo(FundTransactionStatus.PENDING);
        assertThat(tx.getSignalLogEntity().getId()).isEqualTo(signal.getId());

        entityManager.flush();
        entityManager.clear();
        FundEntity reloaded = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloaded.getStatus()).isEqualTo(FundStatus.HOLDING);
        assertThat(reloaded.getOpenedAt()).isNotNull();
    }

    @Test
    void confirmOperation_ADD推进对应档位tierNAddedAt() {
        fund.setStatus(FundStatus.HOLDING);
        strategy.setTier1AddedAt(Instant.parse("2026-06-01T00:00:00Z"));
        SignalLogEntity signal = persistSignal(SignalType.ADD, 2, "TIER2_TRIGGERED");
        entityManager.flush();

        service.confirmOperation(signal.getId(),
                new ConfirmOperationRequest(signal.getId(), new BigDecimal("3000"), null));

        entityManager.flush();
        entityManager.clear();
        FundStrategyEntity reloaded = entityManager.find(FundStrategyEntity.class, strategy.getId());
        assertThat(reloaded.getTier1AddedAt()).isNotNull(); // tier1 保留
        assertThat(reloaded.getTier2AddedAt()).isNotNull(); // tier2 新写入
    }

    @Test
    void confirmOperation_TRAILING_STOP清对应档位且非第四档不改变FundStatus() {
        fund.setStatus(FundStatus.HOLDING);
        strategy.setTier1AddedAt(Instant.parse("2026-06-01T00:00:00Z"));
        strategy.setTier2AddedAt(Instant.parse("2026-06-10T00:00:00Z"));
        SignalLogEntity signal = persistSignal(SignalType.SELL, 2, "TRAILING_STOP");
        entityManager.flush();

        FundTransactionEntity tx = service.confirmOperation(signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, new BigDecimal("500")));

        assertThat(tx.getSource()).isEqualTo(FundTransactionSource.DECREASE);
        assertThat(tx.getShares()).isEqualByComparingTo("500");
        assertThat(tx.getAmount()).isNull();
        assertThat(tx.getNav()).isNull();

        entityManager.flush();
        entityManager.clear();
        FundStrategyEntity reloaded = entityManager.find(FundStrategyEntity.class, strategy.getId());
        assertThat(reloaded.getTier1AddedAt()).isNotNull(); // tier1 保留
        assertThat(reloaded.getTier2AddedAt()).isNull();    // tier2 已清
        FundEntity reloadedFund = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloadedFund.getStatus()).isEqualTo(FundStatus.HOLDING); // 未清空
    }

    @Test
    void confirmOperation_LOGIC_BROKEN一次清空全部档位并CLEARED() {
        fund.setStatus(FundStatus.HOLDING);
        strategy.setTier1AddedAt(Instant.parse("2026-06-01T00:00:00Z"));
        strategy.setTier2AddedAt(Instant.parse("2026-06-10T00:00:00Z"));
        strategy.setTier3AddedAt(Instant.parse("2026-06-15T00:00:00Z"));
        SignalLogEntity signal = persistSignal(SignalType.SELL, null, "LOGIC_BROKEN");
        entityManager.flush();

        service.confirmOperation(signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, new BigDecimal("1000")));

        entityManager.flush();
        entityManager.clear();
        FundStrategyEntity reloaded = entityManager.find(FundStrategyEntity.class, strategy.getId());
        assertThat(reloaded.getTier1AddedAt()).isNull();
        assertThat(reloaded.getTier2AddedAt()).isNull();
        assertThat(reloaded.getTier3AddedAt()).isNull();
        FundEntity reloadedFund = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloadedFund.getStatus()).isEqualTo(FundStatus.CLEARED);
    }

    @Test
    void confirmOperation_REBALANCE不清档位不改FundStatus() {
        fund.setStatus(FundStatus.HOLDING);
        strategy.setTier1AddedAt(Instant.parse("2026-06-01T00:00:00Z"));
        SignalLogEntity signal = persistSignal(SignalType.SELL, null, "REBALANCE");
        entityManager.flush();

        service.confirmOperation(signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, new BigDecimal("200")));

        entityManager.flush();
        entityManager.clear();
        FundStrategyEntity reloaded = entityManager.find(FundStrategyEntity.class, strategy.getId());
        assertThat(reloaded.getTier1AddedAt()).isNotNull(); // 档位保留
        FundEntity reloadedFund = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloadedFund.getStatus()).isEqualTo(FundStatus.HOLDING); // 状态不变
    }

    @Test
    void confirmOperation_BUILD缺少actualAmount抛MISSING_ACTUAL_AMOUNT() {
        SignalLogEntity signal = persistSignal(SignalType.BUILD, null, "BUILD_TRIGGERED");
        entityManager.flush();

        assertThatThrownBy(() -> service.confirmOperation(signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actualAmount");
    }

    @Test
    void confirmOperation_SELL缺少actualShares抛MISSING_ACTUAL_SHARES() {
        SignalLogEntity signal = persistSignal(SignalType.SELL, 1, "TRAILING_STOP");
        entityManager.flush();

        assertThatThrownBy(() -> service.confirmOperation(signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actualShares");
    }

    @Test
    void confirmOperation_signalLogId不存在抛EntityNotFoundException() {
        assertThatThrownBy(() -> service.confirmOperation(999999L,
                new ConfirmOperationRequest(999999L, new BigDecimal("100"), null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void confirmOperation_override实际值与建议不同时只存实际值() {
        SignalLogEntity signal = persistSignal(SignalType.BUILD, null, "BUILD_TRIGGERED");
        // suggestedMeasure 建议金额 10000,用户实际下单 8000(override)
        signal.setSuggestedMeasure(null); // suggestedMeasure 本期非核心,override 校验看 amount
        entityManager.flush();

        FundTransactionEntity tx = service.confirmOperation(signal.getId(),
                new ConfirmOperationRequest(signal.getId(), new BigDecimal("8000"), null));

        // FundTransaction.amount 直接存 actualAmount(8000),不存 diff
        assertThat(tx.getAmount()).isEqualByComparingTo("8000");
    }
}
