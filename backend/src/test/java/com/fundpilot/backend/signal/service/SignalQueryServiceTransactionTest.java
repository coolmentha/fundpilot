package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.enums.TakeProfitPhase;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SignalQueryServiceTransactionTest extends AbstractIntegrationTest {

    @Autowired SignalQueryService service;
    @Autowired EntityManager entityManager;
    @Autowired TradingCalendarRepository tradingCalendarRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE fund CASCADE");
    }

    @Test
    void pending在OpenInView关闭且调用方无事务时可读取LAZY策略() {
        Instant today = ChinaTradingDate.toUtcDate(Instant.now());
        Long signalId = new TransactionTemplate(transactionManager).execute(status -> {
            tradingCalendarRepository.insertTradingDayIfAbsent(
                    today.minus(1, java.time.temporal.ChronoUnit.DAYS));
            FundEntity fund = new FundEntity();
            fund.setFundCode("510500");
            fund.setFundName("中证500ETF");
            fund.setFundCategory(FundCategory.BROAD_BASE);
            fund.setStatus(FundStatus.HOLDING);
            entityManager.persist(fund);

            FundStrategyEntity strategy = new FundStrategyEntity();
            strategy.setFundEntity(fund);
            strategy.setStatus(StrategyParamStatus.EFFECTIVE);
            strategy.setStopLossPullbackPercent(new BigDecimal("0.08"));
            strategy.setTakeProfitPhase(TakeProfitPhase.TRIGGERED);
            entityManager.persist(strategy);

            SignalLogEntity signal = new SignalLogEntity();
            signal.setFundEntity(fund);
            signal.setFundStrategyEntity(strategy);
            signal.setSignalDate(today.minus(2, java.time.temporal.ChronoUnit.DAYS));
            signal.setSignalType(SignalType.SELL);
            signal.setReason(SignalReason.TRAILING_STOP);
            entityManager.persist(signal);
            entityManager.flush();
            strategy.setTriggeredSignalId(signal.getId());
            return signal.getId();
        });

        assertThat(service.pending()).extracting(view -> view.id()).contains(signalId);
    }
}
