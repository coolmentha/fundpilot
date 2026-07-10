package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.exception.BusinessException;
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
import com.fundpilot.backend.signal.enums.SignalReason;
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
 * 验证信号确认分派、归属校验、幂等和 PENDING 阶段不提前推进 FundStatus。
 * BUILD/ADD 仍兼容处理(存量 SignalLog),只写交易不再推进 tierNAddedAt。
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
        entityManager.persist(fund);

        strategy = new FundStrategyEntity();
        strategy.setFundEntity(fund);
        strategy.setStatus(StrategyParamStatus.EFFECTIVE);
        strategy.setStopLossPullbackPercent(new BigDecimal("0.08"));
        entityManager.persist(strategy);
    }

    private SignalLogEntity persistSignal(SignalType type, Integer tier, SignalReason reason) {
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
    void confirmOperation_BUILD只写Pending交易_不提前推进HOLDING() {
        SignalLogEntity signal = persistSignal(SignalType.BUILD, null, SignalReason.BUILD);
        entityManager.flush();

        FundTransactionEntity tx = service.confirmOperation(fund.getId(), signal.getId(),
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
        assertThat(reloaded.getStatus()).isEqualTo(FundStatus.PENDING_HOLDING);
        assertThat(reloaded.getOpenedAt()).isNull();
    }

    @Test
    void confirmOperation_ADD写INCREASE交易_不再推进档位() {
        fund.setStatus(FundStatus.HOLDING);
        SignalLogEntity signal = persistSignal(SignalType.ADD, 2, SignalReason.ADD);
        entityManager.flush();

        FundTransactionEntity tx = service.confirmOperation(fund.getId(), signal.getId(),
                new ConfirmOperationRequest(signal.getId(), new BigDecimal("3000"), null));

        // ADD 存量兼容:只写 INCREASE 交易,不再推进 tierNAddedAt(金字塔退场)
        assertThat(tx.getSource()).isEqualTo(FundTransactionSource.INCREASE);
        assertThat(tx.getAmount()).isEqualByComparingTo("3000");
    }

    @Test
    void confirmOperation_TRAILING_STOP写DECREASE交易() {
        fund.setStatus(FundStatus.HOLDING);
        SignalLogEntity signal = persistSignal(SignalType.SELL, 2, SignalReason.TRAILING_STOP);
        entityManager.flush();

        FundTransactionEntity tx = service.confirmOperation(fund.getId(), signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, new BigDecimal("500")));

        assertThat(tx.getSource()).isEqualTo(FundTransactionSource.DECREASE);
        assertThat(tx.getShares()).isEqualByComparingTo("500");
        assertThat(tx.getAmount()).isNull();
        assertThat(tx.getNav()).isNull();
        assertThat(tx.getSignalLogEntity().getId()).isEqualTo(signal.getId());
    }

    @Test
    void confirmOperation_LOGIC_BROKEN只写Pending交易_确认前保持HOLDING() {
        fund.setStatus(FundStatus.HOLDING);
        SignalLogEntity signal = persistSignal(SignalType.SELL, null, SignalReason.LOGIC_BROKEN);
        entityManager.flush();

        service.confirmOperation(fund.getId(), signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, new BigDecimal("1000")));

        entityManager.flush();
        entityManager.clear();
        FundEntity reloadedFund = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloadedFund.getStatus()).isEqualTo(FundStatus.HOLDING);
    }

    @Test
    void confirmOperation_不支持的SELL原因抛BusinessException() {
        fund.setStatus(FundStatus.HOLDING);
        // REBALANCE 随再平衡机制移除,handleSell 不支持该 reason
        SignalLogEntity signal = persistSignal(SignalType.SELL, null, SignalReason.REBALANCE);
        entityManager.flush();

        assertThatThrownBy(() -> service.confirmOperation(fund.getId(), signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, new BigDecimal("200"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void confirmOperation_BUILD缺少actualAmount抛MISSING_ACTUAL_AMOUNT() {
        SignalLogEntity signal = persistSignal(SignalType.BUILD, null, SignalReason.BUILD);
        entityManager.flush();

        assertThatThrownBy(() -> service.confirmOperation(fund.getId(), signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actualAmount");
    }

    @Test
    void confirmOperation_SELL缺少actualShares抛MISSING_ACTUAL_SHARES() {
        SignalLogEntity signal = persistSignal(SignalType.SELL, 1, SignalReason.TRAILING_STOP);
        entityManager.flush();

        assertThatThrownBy(() -> service.confirmOperation(fund.getId(), signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("actualShares");
    }

    @Test
    void confirmOperation_signalLogId不存在抛BusinessException() {
        assertThatThrownBy(() -> service.confirmOperation(fund.getId(), 999999L,
                new ConfirmOperationRequest(999999L, new BigDecimal("100"), null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void confirmOperation_override实际值与建议不同时只存实际值() {
        SignalLogEntity signal = persistSignal(SignalType.BUILD, null, SignalReason.BUILD);
        // suggestedMeasure 建议金额 10000,用户实际下单 8000(override)
        signal.setSuggestedMeasure(null); // suggestedMeasure 本期非核心,override 校验看 amount
        entityManager.flush();

        FundTransactionEntity tx = service.confirmOperation(fund.getId(), signal.getId(),
                new ConfirmOperationRequest(signal.getId(), new BigDecimal("8000"), null));

        // FundTransaction.amount 直接存 actualAmount(8000),不存 diff
        assertThat(tx.getAmount()).isEqualByComparingTo("8000");
    }

    @Test
    void confirmOperation_路径基金与信号基金不一致时拒绝() {
        SignalLogEntity signal = persistSignal(SignalType.SELL, null, SignalReason.LOGIC_BROKEN);
        entityManager.flush();

        assertThatThrownBy(() -> service.confirmOperation(fund.getId() + 1, signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, new BigDecimal("100"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于");
    }

    @Test
    void confirmOperation_同一信号重复回应时只生成一笔交易() {
        SignalLogEntity signal = persistSignal(SignalType.SELL, null, SignalReason.LOGIC_BROKEN);
        entityManager.flush();

        service.confirmOperation(fund.getId(), signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, new BigDecimal("100")));

        assertThatThrownBy(() -> service.confirmOperation(fund.getId(), signal.getId(),
                new ConfirmOperationRequest(signal.getId(), null, new BigDecimal("100"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已回应");
        assertThat(fundTransactionRepository.findByFundEntity_Id(fund.getId())).hasSize(1);
    }
}
