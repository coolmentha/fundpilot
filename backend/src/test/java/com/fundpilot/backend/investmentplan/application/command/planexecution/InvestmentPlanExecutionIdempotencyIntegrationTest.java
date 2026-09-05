package com.fundpilot.backend.investmentplan.application.command.planexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanInvestmentFactsGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTradingCalendarGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecution;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecutionRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class InvestmentPlanExecutionIdempotencyIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-31T08:00:00Z");
    private static final Instant BUSINESS_DATE = Instant.parse("2026-08-31T00:00:00Z");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private UserAdministrationApi users;
    @Autowired private FundProductApi products;
    @Autowired private FundRepository funds;
    @Autowired private PortfolioFundApi portfolioFunds;
    @Autowired private InvestmentPlanRepository plans;
    @Autowired private PlanTransactionGateway planTransactions;
    @Autowired private PlanPortfolioFundGateway planPortfolioFunds;
    @Autowired private InvestmentPlanExecutionRepository executions;
    @Autowired private TransactionRepository transactions;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentExecutionCreatesOnePendingTransactionAndOneResult() throws Exception {
        Fixture fixture = createFixture();
        InvestmentPlanExecutionCommandHandler commands = executionCommands(planTransactions);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = submit(executor, transaction, commands, fixture, ready, start);
            Future<Boolean> second = submit(executor, transaction, commands, fixture, ready, start);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        List<LedgerTransaction> pending = transaction.execute(status -> transactions.findByPortfolioFundAndStatus(
                fixture.portfolioFundId(), TransactionStatus.PENDING));
        assertThat(pending).singleElement().satisfies(value -> {
            assertThat(value.investmentPlanId()).isEqualTo(fixture.planId());
            assertThat(value.amount()).isEqualByComparingTo("100.00");
        });
        InvestmentPlanExecution result = transaction.execute(status -> executions.find(
                fixture.planId(), BUSINESS_DATE).orElseThrow());
        assertThat(result.result()).isEqualTo(InvestmentPlanExecution.Result.EXECUTED);
        assertThat(result.actualAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void rolledBackExecutionLeavesNoResultAndCanRetryOnce() {
        Fixture fixture = createFixture();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        InvestmentPlanExecutionCommandHandler failed = executionCommands(
                new FailingPlanTransactionGateway(planTransactions));

        assertThrows(IllegalStateException.class, () -> transaction.execute(status ->
                failed.execute(fixture.planId(), NOW)));
        List<LedgerTransaction> pendingAfterFailure = transaction.execute(status ->
                transactions.findByPortfolioFundAndStatus(fixture.portfolioFundId(), TransactionStatus.PENDING));
        assertThat(pendingAfterFailure).isEmpty();
        Optional<InvestmentPlanExecution> resultAfterFailure = transaction.execute(status ->
                executions.find(fixture.planId(), BUSINESS_DATE));
        assertThat(resultAfterFailure).isEmpty();

        InvestmentPlanExecutionCommandHandler retry = executionCommands(planTransactions);
        boolean retryCreated = transaction.execute(status -> retry.execute(fixture.planId(), NOW));
        assertThat(retryCreated).isTrue();
        List<LedgerTransaction> pendingAfterRetry = transaction.execute(status ->
                transactions.findByPortfolioFundAndStatus(fixture.portfolioFundId(), TransactionStatus.PENDING));
        assertThat(pendingAfterRetry).hasSize(1);
        Optional<InvestmentPlanExecution> resultAfterRetry = transaction.execute(status ->
                executions.find(fixture.planId(), BUSINESS_DATE));
        assertThat(resultAfterRetry).isPresent();
    }

    private Future<Boolean> submit(ExecutorService executor, TransactionTemplate transaction,
                                   InvestmentPlanExecutionCommandHandler commands, Fixture fixture,
                                   CountDownLatch ready, CountDownLatch start) {
        return executor.submit(() -> {
            ready.countDown();
            await(start);
            return transaction.execute(status -> commands.execute(fixture.planId(), NOW));
        });
    }

    private InvestmentPlanExecutionCommandHandler executionCommands(PlanTransactionGateway transactions) {
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        when(calendar.isTradingDay(BUSINESS_DATE)).thenReturn(true);
        when(calendar.latestBefore(BUSINESS_DATE)).thenReturn(Optional.empty());
        return new InvestmentPlanExecutionCommandHandler(plans, calendar, transactions, planPortfolioFunds,
                executions, mock(PlanInvestmentFactsGateway.class));
    }

    private Fixture createFixture() {
        long ownerId = users.ensureBootstrapAdmin("plan-idempotent-" + UUID.randomUUID(),
                "integration-test-password").id();
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        return new TransactionTemplate(transactionManager).execute(status -> {
            FundProductApi.ProductReference product = products.ensure(new FundProductApi.EnsureProduct(
                    "I" + suffix, "定投幂等测试基金", null, null));
            FundEntity legacyFund = new FundEntity();
            legacyFund.setOwnerId(ownerId);
            legacyFund.setProductId(product.id());
            legacyFund.setFundCode(product.fundCode());
            legacyFund.setFundName("定投幂等测试基金");
            legacyFund.setFundCategory(FundCategory.BROAD_BASE);
            legacyFund.setFundSubType(FundSubType.INDEX);
            FundEntity savedFund = funds.save(legacyFund);
            PortfolioFundApi.PortfolioFund portfolioFund = portfolioFunds.track(
                    new PortfolioFundApi.TrackPortfolioFund(savedFund.getId(), ownerId, product.id(),
                            true, new BigDecimal("0.30")));
            InvestmentPlan plan = plans.save(InvestmentPlan.create(portfolioFund.id(), ownerId, true,
                    new BigDecimal("100.00"), InvestmentPlanFrequency.WEEKLY, 1, null));
            return new Fixture(ownerId, portfolioFund.id(), plan.id());
        });
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

    private record Fixture(long ownerId, long portfolioFundId, long planId) {
    }

    private static final class FailingPlanTransactionGateway implements PlanTransactionGateway {
        private final PlanTransactionGateway delegate;

        private FailingPlanTransactionGateway(PlanTransactionGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public void createPending(long ownerId, long portfolioFundId, BigDecimal amount,
                                  Instant tradeDate, long planId) {
            delegate.createPending(ownerId, portfolioFundId, amount, tradeDate, planId);
            throw new IllegalStateException("模拟执行事务回滚");
        }

        @Override
        public List<Occurrence> occurrences(long ownerId, Instant startInclusive, Instant endExclusive) {
            return delegate.occurrences(ownerId, startInclusive, endExclusive);
        }

        @Override
        public BigDecimal investedAmount(long ownerId, Instant startInclusive, Instant endExclusive) {
            return delegate.investedAmount(ownerId, startInclusive, endExclusive);
        }
    }
}
