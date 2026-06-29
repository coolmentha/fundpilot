package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.signal.controller.SignalLogView;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
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

/**
 * SignalQueryService 集成测试(issue #16):验证 pending() 工作台过滤 NONE 信号。
 * <p>NONE 是"无建议",后端 confirmOperation 拒绝确认(抛 INVALID_SIGNAL_TYPE),
 * 若 NONE 进了 pending 工作台会变成消不掉的红点。pending() 必须只返回非 NONE 信号。
 */
@Transactional
class SignalQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    SignalQueryService service;
    @Autowired
    EntityManager entityManager;

    private FundEntity fund;
    private FundStrategyEntity strategy;

    @BeforeEach
    void setUp() {
        fund = newFund("510300", "沪深300ETF");
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

    /**
     * 建一只新基金并持久化,供分散插入信号避免撞 uq_signal_log_daily 唯一约束(每基金每日唯一)。
     */
    private FundEntity newFund(String code, String name) {
        FundEntity f = new FundEntity();
        f.setFundCode(code);
        f.setFundName(name);
        f.setFundCategory(FundCategory.BROAD_BASE);
        f.setStatus(FundStatus.HOLDING);
        f.setPlannedTotalAmount(new BigDecimal("100000"));
        return f;
    }

    private SignalLogEntity persistSignal(FundEntity f, SignalType type, Integer tier, SignalReason reason) {
        SignalLogEntity log = new SignalLogEntity();
        log.setFundEntity(f);
        log.setFundStrategyEntity(strategy);
        log.setSignalDate(Instant.now());
        log.setSignalType(type);
        log.setTriggerTier(tier);
        log.setReason(reason);
        entityManager.persist(log);
        return log;
    }

    @Test
    void pending不返回NONE信号_只含可确认的BUILD_ADD_SELL() {
        // 4 只基金各插 1 条信号,分散到不同基金避免撞 uq_signal_log_daily(每基金每日唯一)
        FundEntity f1 = newFund("510300", "沪深300ETF");
        FundEntity f2 = newFund("159915", "创业板ETF");
        FundEntity f3 = newFund("512760", "半导体ETF");
        FundEntity f4 = newFund("512010", "医药ETF");
        entityManager.persist(f1);
        entityManager.persist(f2);
        entityManager.persist(f3);
        entityManager.persist(f4);

        persistSignal(f1, SignalType.NONE, null, SignalReason.NO_TIER_TO_SELL);
        persistSignal(f2, SignalType.BUILD, null, SignalReason.BUILD);
        persistSignal(f3, SignalType.ADD, 2, SignalReason.ADD);
        persistSignal(f4, SignalType.SELL, 2, SignalReason.TRAILING_STOP);
        entityManager.flush();
        entityManager.clear();

        List<SignalLogView> pending = service.pending();

        assertThat(pending).extracting(SignalLogView::signalType)
                .doesNotContain(SignalType.NONE)
                .contains(SignalType.BUILD, SignalType.ADD, SignalType.SELL);
        assertThat(pending).hasSize(3);
    }
}
