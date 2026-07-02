package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 信号生成集成测试(issue #62):验证从 DB 装配 State/Position 后产出真实 SELL 信号。
 */
@Transactional
class SignalGenerationServiceTest extends AbstractIntegrationTest {

    @Autowired SignalGenerationService signalGenerationService;
    @Autowired SignalLogRepository signalLogRepository;
    @Autowired FundTransactionRepository fundTransactionRepository;
    @Autowired EntityManager entityManager;

    /**
     * #62:已盈利达启动门槛 + 连2日跌破止盈线 → 产出 SELL 信号。
     * <p>宽基:1000 份成本 1.0=投入 1000。净值 1.0(建仓)→1.8(启动,yield80%≥50%,High1800,线1530)
     * →1.5(跌破1,市值1500<1530)→1.5(跌破2,今日,应 SELL)。
     */
    @Test
    void 达启动门槛连2日跌破止盈线_产出SELL信号() {
        FundEntity fund = persistBroadFund();
        FundStrategyEntity strategy = persistStrategy(fund);
        persistConfirmedBuy(fund, new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("1.0"),
                Instant.parse("2025-01-01T00:00:00Z"));
        // 净值序列(回放至今日前):宽基启动50%,peakYield80%→档80~150%→18%,线=High1800×0.82=1476
        persistNav(fund, "1.0", Instant.parse("2025-01-01T00:00:00Z"));
        persistNav(fund, "1.8", Instant.parse("2025-03-01T00:00:00Z")); // 启动 High1800 线1476
        persistNav(fund, "1.4", Instant.parse("2025-04-01T00:00:00Z")); // 跌破1(市值1400<1476)
        persistNav(fund, "1.4", Instant.parse("2025-04-02T00:00:00Z")); // 今日(跌破2,应 SELL)
        entityManager.flush();
        entityManager.clear();

        signalGenerationService.generateDailySignals(Instant.parse("2025-04-02T00:00:00Z"));

        List<SignalLogEntity> logs = signalLogRepository.findByFundEntity_IdAndSignalDateBetween(
                fund.getId(),
                Instant.parse("2025-04-02T00:00:00Z"),
                Instant.parse("2025-04-03T00:00:00Z"));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getSignalType()).isEqualTo(SignalType.SELL);
    }

    /**
     * #62:未达启动门槛(收益低)→ 产出 NONE 信号。
     */
    @Test
    void 未达启动门槛_产出NONE信号() {
        FundEntity fund = persistBroadFund();
        persistStrategy(fund);
        persistConfirmedBuy(fund, new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("1.0"),
                Instant.parse("2025-01-01T00:00:00Z"));
        persistNav(fund, "1.0", Instant.parse("2025-01-01T00:00:00Z"));
        persistNav(fund, "1.1", Instant.parse("2025-04-01T00:00:00Z")); // yield10% < 50% 未启动
        persistNav(fund, "1.1", Instant.parse("2025-04-02T00:00:00Z")); // 今日
        entityManager.flush();
        entityManager.clear();

        signalGenerationService.generateDailySignals(Instant.parse("2025-04-02T00:00:00Z"));

        List<SignalLogEntity> logs = signalLogRepository.findByFundEntity_IdAndSignalDateBetween(
                fund.getId(),
                Instant.parse("2025-04-02T00:00:00Z"),
                Instant.parse("2025-04-03T00:00:00Z"));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getSignalType()).isEqualTo(SignalType.NONE);
    }

    private FundEntity persistBroadFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setStatus(FundStatus.HOLDING);
        fund.setDcaAmount(new BigDecimal("1000"));
        fund.setOpenedAt(Instant.parse("2025-01-01T00:00:00Z"));
        entityManager.persist(fund);
        return fund;
    }

    private FundStrategyEntity persistStrategy(FundEntity fund) {
        FundStrategyEntity s = new FundStrategyEntity();
        s.setFundEntity(fund);
        s.setStatus(StrategyParamStatus.EFFECTIVE);
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
        s.setCooldownDays(2); // 短冷却便于测试
        entityManager.persist(s);
        return s;
    }

    private void persistConfirmedBuy(FundEntity fund, BigDecimal amount, BigDecimal shares, BigDecimal nav, Instant confirmTime) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.INCREASE);
        tx.setAmount(amount);
        tx.setShares(shares);
        tx.setNav(nav);
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        tx.setConfirmTime(confirmTime);
        entityManager.persist(tx);
    }

    private void persistNav(FundEntity fund, String accumulatedNav, Instant navDate) {
        FundNavHistoryEntity nav = new FundNavHistoryEntity();
        nav.setFundEntity(fund);
        nav.setNavDate(navDate);
        nav.setNav(new BigDecimal(accumulatedNav));
        nav.setAccumulatedNav(new BigDecimal(accumulatedNav));
        entityManager.persist(nav);
    }
}