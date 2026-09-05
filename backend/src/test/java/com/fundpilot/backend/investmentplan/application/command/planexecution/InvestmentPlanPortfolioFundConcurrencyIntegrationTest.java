package com.fundpilot.backend.investmentplan.application.command.planexecution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionCommandHandler;
import com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionFailure;
import com.fundpilot.backend.accounting.application.gateway.portfoliocorrection.CorrectablePortfolioFundGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.PendingTransactionRepository;
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
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecutionRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanFrequency;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
class InvestmentPlanPortfolioFundConcurrencyIntegrationTest {

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
    @Autowired private CorrectablePortfolioFundGateway correctablePortfolioFunds;
    @Autowired private PositionRepository positions;
    @Autowired private TransactionRepository transactions;
    @Autowired private PendingTransactionRepository pendingTransactions;
    @Autowired private LedgerEventGateway ledgerEvents;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void executionFirstIsSerializedBeforeVoidPendingCheck() throws Exception {
        Fixture fixture = createFixture();
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        when(calendar.isTradingDay(BUSINESS_DATE)).thenReturn(true);
        when(calendar.latestBefore(BUSINESS_DATE)).thenReturn(Optional.empty());
        LockingPlanPortfolioFundGateway lockedFunds = new LockingPlanPortfolioFundGateway(planPortfolioFunds);
        InvestmentPlanExecutionCommandHandler executionCommands = new InvestmentPlanExecutionCommandHandler(
                plans, calendar, planTransactions, lockedFunds, executions, mock(PlanInvestmentFactsGateway.class));
        ObservingCorrectablePortfolioFundGateway voidFunds =
                new ObservingCorrectablePortfolioFundGateway(correctablePortfolioFunds);
        ObservingPendingTransactionRepository pendingGate =
                new ObservingPendingTransactionRepository(pendingTransactions);
        PortfolioCorrectionCommandHandler voidCommands = new PortfolioCorrectionCommandHandler(
                voidFunds, positions, transactions, pendingGate, ledgerEvents,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Boolean> execution = null;
        try {
            execution = executor.submit(() -> transaction.execute(status ->
                    executionCommands.execute(fixture.planId(), NOW)));
            assertThat(lockedFunds.lockAcquired.await(10, TimeUnit.SECONDS)).isTrue();

            Future<PortfolioCorrectionCommandHandler.VoidResult> voidResult = executor.submit(() -> transaction.execute(status ->
                    voidCommands.voidPortfolioFund(fixture.ownerId(), fixture.portfolioFundId(),
                            "基金代码录入错误", true)));
            assertThat(voidFunds.lockRequested.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(pendingGate.checked.await(500, TimeUnit.MILLISECONDS))
                    .as("作废必须先锁定组合基金，再检查待确认交易").isFalse();

            lockedFunds.release.countDown();
            assertThat(execution.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(pendingGate.checked.await(10, TimeUnit.SECONDS)).isTrue();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> voidResult.get(10, TimeUnit.SECONDS));
            assertThat(failure.getCause()).isInstanceOfSatisfying(
                    com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionFailure.class,
                    value -> assertThat(value.code()).isEqualTo(
                            com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_HAS_PENDING_TRANSACTIONS));
        } finally {
            lockedFunds.release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        PortfolioFundApi.PortfolioFund stored = new TransactionTemplate(transactionManager)
                .execute(status -> portfolioFunds.findById(fixture.portfolioFundId()).orElseThrow());
        assertThat(stored.validity()).isEqualTo(PortfolioFundApi.Validity.TRACKED);
        List<com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction> pending =
                new TransactionTemplate(transactionManager).execute(status -> transactions.findByPortfolioFundAndStatus(
                        fixture.portfolioFundId(), com.fundpilot.backend.accounting.domain.transaction.TransactionStatus.PENDING));
        assertThat(pending).singleElement().satisfies(value -> {
            assertThat(value.investmentPlanId()).isEqualTo(fixture.planId());
            assertThat(value.amount()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void voidFirstIsSerializedBeforePlanExecution() throws Exception {
        Fixture fixture = createFixture();
        HoldingVoidPortfolioFundGateway voidFunds = new HoldingVoidPortfolioFundGateway(correctablePortfolioFunds);
        ObservingPlanPortfolioFundGateway executionFunds = new ObservingPlanPortfolioFundGateway(planPortfolioFunds);
        InvestmentPlanExecutionCommandHandler executionCommands = executionCommands(executionFunds);
        PortfolioCorrectionCommandHandler voidCommands = correctionCommands(voidFunds, pendingTransactions);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PortfolioCorrectionCommandHandler.VoidResult> voidResult = executor.submit(() -> transaction.execute(status ->
                    voidCommands.voidPortfolioFund(fixture.ownerId(), fixture.portfolioFundId(),
                            "基金代码录入错误", true)));
            assertThat(voidFunds.readHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> execution = executor.submit(() -> transaction.execute(status ->
                    executionCommands.execute(fixture.planId(), NOW)));
            assertThat(executionFunds.lockAcquired.await(500, TimeUnit.MILLISECONDS))
                    .as("作废持锁期间定投执行不得取得组合基金锁").isFalse();

            voidFunds.release.countDown();
            assertThat(voidResult.get(10, TimeUnit.SECONDS).changed()).isTrue();
            assertThat(execution.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            voidFunds.release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        PortfolioFundApi.PortfolioFund stored = transaction.execute(status ->
                portfolioFunds.findById(fixture.portfolioFundId()).orElseThrow());
        assertThat(stored.validity()).isEqualTo(PortfolioFundApi.Validity.VOIDED);
        List<LedgerTransaction> pendingAfterVoid = transaction.execute(status ->
                transactions.findByPortfolioFundAndStatus(fixture.portfolioFundId(), TransactionStatus.PENDING));
        assertThat(pendingAfterVoid).isEmpty();
    }

    @Test
    void rolledBackExecutionThenVoidPreventsRetry() {
        Fixture fixture = createFixture();
        InvestmentPlanExecutionCommandHandler failedExecution = executionCommands(
                new FailingPlanTransactionGateway(planTransactions), planPortfolioFunds);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.execute(status ->
                failedExecution.execute(fixture.planId(), NOW)));
        List<LedgerTransaction> pendingAfterFailedExecution = transaction.execute(status ->
                transactions.findByPortfolioFundAndStatus(fixture.portfolioFundId(), TransactionStatus.PENDING));
        assertThat(pendingAfterFailedExecution).isEmpty();

        PortfolioCorrectionCommandHandler voidCommands = correctionCommands(
                correctablePortfolioFunds, pendingTransactions);
        assertThat(transaction.execute(status -> voidCommands.voidPortfolioFund(
                fixture.ownerId(), fixture.portfolioFundId(), "基金代码录入错误", true)).changed()).isTrue();

        InvestmentPlanExecutionCommandHandler retry = executionCommands(planPortfolioFunds);
        boolean retryResult = transaction.execute(status -> retry.execute(fixture.planId(), NOW));
        assertThat(retryResult).isFalse();
        List<LedgerTransaction> pendingAfterRetry = transaction.execute(status ->
                transactions.findByPortfolioFundAndStatus(fixture.portfolioFundId(), TransactionStatus.PENDING));
        assertThat(pendingAfterRetry).isEmpty();
    }

    @Test
    void rolledBackVoidThenExecutionCanRetryAndVoidStillSeesPending() {
        Fixture fixture = createFixture();
        PortfolioCorrectionCommandHandler failedVoid = correctionCommands(
                new FailingCorrectablePortfolioFundGateway(correctablePortfolioFunds), pendingTransactions);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.execute(status ->
                failedVoid.voidPortfolioFund(fixture.ownerId(), fixture.portfolioFundId(),
                        "基金代码录入错误", true)));
        PortfolioFundApi.Validity validityAfterFailedVoid = transaction.execute(status ->
                portfolioFunds.findById(fixture.portfolioFundId()).orElseThrow().validity());
        assertThat(validityAfterFailedVoid).isEqualTo(PortfolioFundApi.Validity.TRACKED);

        InvestmentPlanExecutionCommandHandler execution = executionCommands(planPortfolioFunds);
        boolean executionResult = transaction.execute(status -> execution.execute(fixture.planId(), NOW));
        assertThat(executionResult).isTrue();
        PortfolioCorrectionCommandHandler retryVoid = correctionCommands(
                correctablePortfolioFunds, pendingTransactions);
        PortfolioCorrectionFailure failure = assertThrows(PortfolioCorrectionFailure.class, () -> transaction.execute(status ->
                retryVoid.voidPortfolioFund(fixture.ownerId(), fixture.portfolioFundId(),
                        "基金代码录入错误", true)));
        assertThat(failure.code()).isEqualTo(
                PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_HAS_PENDING_TRANSACTIONS);
        List<LedgerTransaction> pendingAfterExecution = transaction.execute(status ->
                transactions.findByPortfolioFundAndStatus(fixture.portfolioFundId(), TransactionStatus.PENDING));
        assertThat(pendingAfterExecution).hasSize(1);
    }

    private InvestmentPlanExecutionCommandHandler executionCommands(PlanPortfolioFundGateway portfolioFunds) {
        return executionCommands(planTransactions, portfolioFunds);
    }

    private InvestmentPlanExecutionCommandHandler executionCommands(
            PlanTransactionGateway transactions, PlanPortfolioFundGateway portfolioFunds) {
        PlanTradingCalendarGateway calendar = mock(PlanTradingCalendarGateway.class);
        when(calendar.isTradingDay(BUSINESS_DATE)).thenReturn(true);
        when(calendar.latestBefore(BUSINESS_DATE)).thenReturn(Optional.empty());
        return new InvestmentPlanExecutionCommandHandler(plans, calendar, transactions, portfolioFunds,
                executions, mock(PlanInvestmentFactsGateway.class));
    }

    private PortfolioCorrectionCommandHandler correctionCommands(
            CorrectablePortfolioFundGateway portfolioFunds, PendingTransactionRepository pending) {
        return new PortfolioCorrectionCommandHandler(portfolioFunds, positions, transactions, pending,
                ledgerEvents, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Fixture createFixture() {
        long ownerId = users.ensureBootstrapAdmin("plan-void-" + UUID.randomUUID(), "integration-test-password").id();
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        return new TransactionTemplate(transactionManager).execute(status -> {
            FundProductApi.ProductReference product = products.ensure(new FundProductApi.EnsureProduct(
                    "T" + suffix, "定投作废竞态测试基金", null, null));
            FundEntity legacyFund = new FundEntity();
            legacyFund.setOwnerId(ownerId);
            legacyFund.setProductId(product.id());
            legacyFund.setFundCode(product.fundCode());
            legacyFund.setFundName("定投作废竞态测试基金");
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

    private record Fixture(long ownerId, long portfolioFundId, long planId) {
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

    private static final class ObservingPendingTransactionRepository implements PendingTransactionRepository {
        private final PendingTransactionRepository delegate;
        private final CountDownLatch checked = new CountDownLatch(1);

        private ObservingPendingTransactionRepository(PendingTransactionRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean existsByPortfolioFund(long portfolioFundId, Long legacyFundId) {
            boolean result = delegate.existsByPortfolioFund(portfolioFundId, legacyFundId);
            checked.countDown();
            return result;
        }
    }

    private static final class ObservingCorrectablePortfolioFundGateway
            implements CorrectablePortfolioFundGateway {
        private final CorrectablePortfolioFundGateway delegate;
        private final CountDownLatch lockRequested = new CountDownLatch(1);

        private ObservingCorrectablePortfolioFundGateway(CorrectablePortfolioFundGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<PortfolioFund> findOwned(long ownerId, long portfolioFundId) {
            return delegate.findOwned(ownerId, portfolioFundId);
        }

        @Override
        public Optional<PortfolioFund> findOwnedForUpdate(long ownerId, long portfolioFundId) {
            lockRequested.countDown();
            return delegate.findOwnedForUpdate(ownerId, portfolioFundId);
        }

        @Override
        public VoidResult voidPortfolioFund(long ownerId, long portfolioFundId, long actorId,
                                            String reason, Instant occurredAt) {
            return delegate.voidPortfolioFund(ownerId, portfolioFundId, actorId, reason, occurredAt);
        }
    }

    private static final class LockingPlanPortfolioFundGateway implements PlanPortfolioFundGateway {
        private final PlanPortfolioFundGateway delegate;
        private final CountDownLatch lockAcquired = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private LockingPlanPortfolioFundGateway(PlanPortfolioFundGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<PortfolioFund> findTrackedForExecution(long ownerId, long portfolioFundId) {
            Optional<PortfolioFund> result = delegate.findTrackedForExecution(ownerId, portfolioFundId);
            lockAcquired.countDown();
            await(release);
            return result;
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
        public List<PortfolioFund> findTrackedByOwner(long ownerId) {
            return delegate.findTrackedByOwner(ownerId);
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
    }

    private static final class ObservingPlanPortfolioFundGateway implements PlanPortfolioFundGateway {
        private final PlanPortfolioFundGateway delegate;
        private final CountDownLatch lockAcquired = new CountDownLatch(1);

        private ObservingPlanPortfolioFundGateway(PlanPortfolioFundGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<PortfolioFund> findTrackedForExecution(long ownerId, long portfolioFundId) {
            Optional<PortfolioFund> result = delegate.findTrackedForExecution(ownerId, portfolioFundId);
            lockAcquired.countDown();
            return result;
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
        public List<PortfolioFund> findTrackedByOwner(long ownerId) {
            return delegate.findTrackedByOwner(ownerId);
        }
    }

    private static final class HoldingVoidPortfolioFundGateway implements CorrectablePortfolioFundGateway {
        private final CorrectablePortfolioFundGateway delegate;
        private final CountDownLatch readHeld = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private HoldingVoidPortfolioFundGateway(CorrectablePortfolioFundGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<PortfolioFund> findOwned(long ownerId, long portfolioFundId) {
            return readAndHold(() -> delegate.findOwned(ownerId, portfolioFundId));
        }

        @Override
        public Optional<PortfolioFund> findOwnedForUpdate(long ownerId, long portfolioFundId) {
            return readAndHold(() -> delegate.findOwnedForUpdate(ownerId, portfolioFundId));
        }

        @Override
        public VoidResult voidPortfolioFund(long ownerId, long portfolioFundId, long actorId,
                                            String reason, Instant occurredAt) {
            return delegate.voidPortfolioFund(ownerId, portfolioFundId, actorId, reason, occurredAt);
        }

        private Optional<PortfolioFund> readAndHold(Supplier<Optional<PortfolioFund>> read) {
            Optional<PortfolioFund> result = read.get();
            readHeld.countDown();
            await(release);
            return result;
        }
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

    private static final class FailingCorrectablePortfolioFundGateway implements CorrectablePortfolioFundGateway {
        private final CorrectablePortfolioFundGateway delegate;

        private FailingCorrectablePortfolioFundGateway(CorrectablePortfolioFundGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<PortfolioFund> findOwned(long ownerId, long portfolioFundId) {
            return delegate.findOwned(ownerId, portfolioFundId);
        }

        @Override
        public Optional<PortfolioFund> findOwnedForUpdate(long ownerId, long portfolioFundId) {
            return delegate.findOwnedForUpdate(ownerId, portfolioFundId);
        }

        @Override
        public VoidResult voidPortfolioFund(long ownerId, long portfolioFundId, long actorId,
                                            String reason, Instant occurredAt) {
            delegate.voidPortfolioFund(ownerId, portfolioFundId, actorId, reason, occurredAt);
            throw new IllegalStateException("模拟作废事务回滚");
        }
    }
}
