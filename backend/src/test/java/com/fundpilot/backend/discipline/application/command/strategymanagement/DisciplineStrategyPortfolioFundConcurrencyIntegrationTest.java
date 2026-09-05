package com.fundpilot.backend.discipline.application.command.strategymanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fundpilot.backend.discipline.application.gateway.strategymanagement.StrategyPortfolioFundGateway;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DisciplineStrategyPortfolioFundConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private DisciplineStrategyCommandHandler commands;
    @Autowired private DisciplineStrategyRepository strategies;
    @Autowired private StrategyPortfolioFundGateway strategyFunds;
    @Autowired private PortfolioFundApi portfolioFunds;
    @Autowired private FundProductApi products;
    @Autowired private PlatformTransactionManager transactionManager;

    @ParameterizedTest(name = "作废与策略{0}串行")
    @ValueSource(strings = {"create", "update", "activate"})
    void voidFirstPreventsConcurrentStrategyWrite(String action) throws Exception {
        var product = products.ensure(new FundProductApi.EnsureProduct(
                "D" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                "纪律作废竞态测试基金", null, null));
        var fund = portfolioFunds.track(new PortfolioFundApi.TrackPortfolioFund(
                null, testActorId(), product.id(), true, new BigDecimal("0.30")));
        Long strategyId = "create".equals(action)
                ? null
                : commands.createForPortfolioFund(testActorId(), fund.id(), input()).id();
        var observedFunds = new ObservingStrategyPortfolioFundGateway(strategyFunds);
        var writer = new DisciplineStrategyCommandHandler(strategies, observedFunds);
        var transaction = new TransactionTemplate(transactionManager);
        CountDownLatch voided = new CountDownLatch(1);
        CountDownLatch releaseVoid = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> voidResult = executor.submit(() -> transaction.executeWithoutResult(status -> {
                portfolioFunds.voidPortfolioFund(new PortfolioFundApi.VoidPortfolioFund(
                        testActorId(), fund.id(), testActorId(), "组合基金录入错误",
                        Instant.parse("2026-09-04T00:00:00Z")));
                voided.countDown();
                await(releaseVoid);
            }));
            assertThat(voided.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> writeResult = executor.submit(() -> transaction.executeWithoutResult(status -> {
                if ("create".equals(action)) {
                    writer.createForPortfolioFund(testActorId(), fund.id(), input());
                } else if ("update".equals(action)) {
                    writer.update(testActorId(), strategyId, input());
                } else {
                    writer.activate(testActorId(), strategyId);
                }
            }));
            assertThat(observedFunds.lockRequested.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(observedFunds.lockReturned.await(500, TimeUnit.MILLISECONDS))
                    .as("作废事务提交前策略写入不得取得组合基金锁")
                    .isFalse();

            releaseVoid.countDown();
            voidResult.get(10, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> writeResult.get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause()).isInstanceOfSatisfying(BusinessException.class,
                    exception -> assertThat(exception.getCode()).isEqualTo("ILLEGAL_STATE_TRANSITION"));
        } finally {
            releaseVoid.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(portfolioFunds.findById(fund.id()).orElseThrow().validity())
                .isEqualTo(PortfolioFundApi.Validity.VOIDED);
        assertThat(strategies.findEffectiveByPortfolioFundId(fund.id())).isEmpty();
    }

    private static DisciplineStrategyCommandHandler.Input input() {
        return new DisciplineStrategyCommandHandler.Input(
                new BigDecimal("0.15"), new BigDecimal("0.06"), new BigDecimal("0.50"),
                new BigDecimal("0.50"), new BigDecimal("0.20"), 10,
                "BROAD_BASE", 1, false);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("并发测试屏障等待超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("并发测试屏障被中断", exception);
        }
    }

    private static final class ObservingStrategyPortfolioFundGateway
            implements StrategyPortfolioFundGateway {
        private final StrategyPortfolioFundGateway delegate;
        private final CountDownLatch lockRequested = new CountDownLatch(1);
        private final CountDownLatch lockReturned = new CountDownLatch(1);

        private ObservingStrategyPortfolioFundGateway(StrategyPortfolioFundGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public PortfolioFund requireTrackedByLegacyFund(long ownerId, long legacyFundId) {
            return delegate.requireTrackedByLegacyFund(ownerId, legacyFundId);
        }

        @Override
        public PortfolioFund requireTracked(long ownerId, long portfolioFundId) {
            return delegate.requireTracked(ownerId, portfolioFundId);
        }

        @Override
        public PortfolioFund requireTrackedForUpdate(long ownerId, long portfolioFundId) {
            lockRequested.countDown();
            try {
                return delegate.requireTrackedForUpdate(ownerId, portfolioFundId);
            } finally {
                lockReturned.countDown();
            }
        }
    }
}
