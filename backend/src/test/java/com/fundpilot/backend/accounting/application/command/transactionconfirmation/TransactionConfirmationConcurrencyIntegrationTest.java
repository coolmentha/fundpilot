package com.fundpilot.backend.accounting.application.command.transactionconfirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.accounting.application.command.transactionledger.TransactionLedgerCommandHandler;
import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementFeeGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementNavGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.ledgerreplay.LedgerReplay;
import com.fundpilot.backend.accounting.domain.lot.FeeSchedule;
import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.platform.transaction.RequiresNewTransactionExecutor;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
class TransactionConfirmationConcurrencyIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-31T08:00:00Z");
    private static final Instant TRADE_DATE = Instant.parse("2026-08-30T16:00:00Z");

    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserAdministrationApi users;
    @Autowired
    private FundProductApi products;
    @Autowired
    private FundRepository funds;
    @Autowired
    private PortfolioFundApi portfolioFunds;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private TransactionLedgerCommandHandler ledgerCommands;
    @Autowired
    private LotRepository lots;
    @Autowired
    private TradedPortfolioFundGateway tradedPortfolioFunds;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentSellConfirmation_firstPendingWinsAndRetryStaysRejected() throws Exception {
        runScenario(true);
    }

    @Test
    void concurrentSellConfirmation_secondPendingWinsAndRetryStaysRejected() throws Exception {
        runScenario(false);
    }

    @Test
    void concurrentOppositeConversions_lockFundsInSameOrderAndBothComplete() throws Exception {
        MultiFundFixture fixture = createMultiFundFixture(true);
        SettlementFeeGateway fees = feeGateway(fixture);
        SettlementNavGateway navs = navGateway(fixture, new AtomicBoolean(true));
        MultiFundLockGate gate = new MultiFundLockGate(tradedPortfolioFunds);
        TransactionConfirmationCommandHandler confirmations = confirmations(gate, fees, navs);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var forward = executor.submit(() -> transaction.execute(status ->
                    confirmations.confirm(fixture.ownerId(), fixture.forwardOutId())));
            assertThat(gate.firstLocked.await(10, TimeUnit.SECONDS)).isTrue();

            var reverse = executor.submit(() -> transaction.execute(status ->
                    confirmations.confirm(fixture.ownerId(), fixture.reverseOutId())));
            assertThat(gate.secondAttempted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(gate.firstPortfolioFundId.get()).isEqualTo(fixture.firstPortfolioFundId());
            assertThat(gate.secondPortfolioFundId.get()).isEqualTo(fixture.firstPortfolioFundId());
            assertThat(gate.secondLockReturned.await(500, TimeUnit.MILLISECONDS))
                    .as("方向相反的转换也应先竞争同一较小组合基金行锁").isFalse();
            gate.releaseFirst.countDown();

            assertThat(forward.get(10, TimeUnit.SECONDS)).hasSize(2);
            assertThat(reverse.get(10, TimeUnit.SECONDS)).hasSize(2);
        } finally {
            gate.releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        MultiFundFacts facts = readMultiFundFacts(fixture);
        assertThat(facts.firstNetShares()).isEqualTo("10");
        assertThat(facts.secondNetShares()).isEqualTo("10");
        assertThat(facts.firstLotShares()).isEqualTo("10");
        assertThat(facts.secondLotShares()).isEqualTo("10");
        assertThat(facts.pendingIds()).isEmpty();
        assertThat(facts.redemptions()).hasSize(2);

        TransactionConfirmationFailure retry = retryFailureOf(
                confirmations, fixture.ownerId(), fixture.forwardOutId());
        assertThat(retry.code()).isEqualTo(
                TransactionConfirmationFailure.Code.TRANSACTION_ALREADY_CONFIRMED);
        assertThat(readMultiFundFacts(fixture)).usingRecursiveComparison().isEqualTo(facts);
    }

    @Test
    void conversionTargetNavFailure_rollsBackBothLegsAndRetryDoesNotDuplicateFacts() {
        MultiFundFixture fixture = createMultiFundFixture(false);
        AtomicBoolean targetNavAvailable = new AtomicBoolean(false);
        TransactionConfirmationCommandHandler confirmations = confirmations(
                tradedPortfolioFunds, feeGateway(fixture), navGateway(fixture, targetNavAvailable));

        TransactionConfirmationFailure failure = retryFailureOf(
                confirmations, fixture.ownerId(), fixture.forwardOutId());
        assertThat(failure.code()).isEqualTo(TransactionConfirmationFailure.Code.NAV_UNAVAILABLE);
        MultiFundFacts rolledBack = readMultiFundFacts(fixture);
        assertThat(rolledBack.firstNetShares()).isEqualTo("10");
        assertThat(rolledBack.secondNetShares()).isEqualTo("10");
        assertThat(rolledBack.firstLotShares()).isEqualTo("10");
        assertThat(rolledBack.secondLotShares()).isEqualTo("10");
        assertThat(rolledBack.pendingIds()).hasSize(2);
        assertThat(rolledBack.redemptions()).isEmpty();

        targetNavAvailable.set(true);
        new TransactionTemplate(transactionManager).execute(status ->
                confirmations.confirm(fixture.ownerId(), fixture.forwardOutId()));
        MultiFundFacts confirmed = readMultiFundFacts(fixture);
        assertThat(confirmed.firstNetShares()).isEqualTo("4");
        assertThat(confirmed.secondNetShares()).isEqualTo("16");
        assertThat(confirmed.firstLotShares()).isEqualTo("4");
        assertThat(confirmed.secondLotShares()).isEqualTo("16");
        assertThat(confirmed.pendingIds()).isEmpty();
        assertThat(confirmed.redemptions()).hasSize(1);

        TransactionConfirmationFailure retry = retryFailureOf(
                confirmations, fixture.ownerId(), fixture.forwardOutId());
        assertThat(retry.code()).isEqualTo(
                TransactionConfirmationFailure.Code.TRANSACTION_ALREADY_CONFIRMED);
        assertThat(readMultiFundFacts(fixture)).usingRecursiveComparison().isEqualTo(confirmed);
    }

    private void runScenario(boolean firstPendingStartsFirst) throws Exception {
        Fixture fixture = createFixture();
        SettlementFeeGateway fees = mock(SettlementFeeGateway.class);
        SettlementNavGateway navs = mock(SettlementNavGateway.class);
        when(fees.feeScheduleOf(fixture.fundProductId())).thenReturn(FeeSchedule.none());
        when(navs.unitNavOn(fixture.fundProductId(), Instant.parse("2026-08-31T00:00:00Z")))
                .thenReturn(Optional.of(BigDecimal.ONE));

        LockGate gate = new LockGate(tradedPortfolioFunds);
        TransactionConfirmationCommandHandler confirmations = new TransactionConfirmationCommandHandler(
                transactions, lots, gate, fees, navs, mock(PositionCommandHandler.class),
                mock(LedgerEventGateway.class), mock(RequiresNewTransactionExecutor.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        long winnerId = firstPendingStartsFirst ? fixture.firstPendingId() : fixture.secondPendingId();
        long loserId = firstPendingStartsFirst ? fixture.secondPendingId() : fixture.firstPendingId();

        try {
            var first = executor.submit(() -> transaction.execute(status ->
                    confirmations.confirm(fixture.ownerId(), winnerId)));
            assertThat(gate.firstLocked.await(10, TimeUnit.SECONDS))
                    .as("第一笔确认应取得组合基金行锁").isTrue();

            var second = executor.submit(() -> transaction.execute(status ->
                    confirmations.confirm(fixture.ownerId(), loserId)));
            assertThat(gate.secondAttempted.await(10, TimeUnit.SECONDS))
                    .as("第二笔确认应在组合基金行锁处排队").isTrue();
            assertThat(gate.secondLockReturned.await(500, TimeUnit.MILLISECONDS))
                    .as("第一笔持锁期间第二笔不得完成 findForUpdate").isFalse();
            gate.releaseFirst.countDown();
            assertThat(gate.secondLockReturned.await(10, TimeUnit.SECONDS))
                    .as("第一笔提交后第二笔应完成 findForUpdate").isTrue();

            assertThat(first.get(10, TimeUnit.SECONDS)).containsExactly(winnerId);
            TransactionConfirmationFailure failure = failureOf(second);
            assertThat(failure.code()).isEqualTo(
                    TransactionConfirmationFailure.Code.INSUFFICIENT_HOLDING_SHARES);
        } finally {
            gate.releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        Facts afterConcurrentConfirmation = readFacts(fixture);
        assertThat(afterConcurrentConfirmation.netShares()).isEqualTo("4");
        assertThat(afterConcurrentConfirmation.remainingLotShares()).isEqualTo("4");
        assertThat(afterConcurrentConfirmation.confirmedIds())
                .containsExactlyInAnyOrder(fixture.initialTransactionId(), winnerId);
        assertThat(afterConcurrentConfirmation.pendingIds()).containsExactly(loserId);
        assertThat(afterConcurrentConfirmation.redemptions()).containsExactly(winnerId + ":6");

        TransactionConfirmationFailure retryFailure = retryFailureOf(confirmations, fixture.ownerId(), loserId);
        assertThat(retryFailure.code()).isEqualTo(
                TransactionConfirmationFailure.Code.INSUFFICIENT_HOLDING_SHARES);
        assertThat(readFacts(fixture)).usingRecursiveComparison()
                .isEqualTo(afterConcurrentConfirmation);
    }

    private Facts readFacts(Fixture fixture) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            List<LedgerTransaction> confirmed = transactions.findByPortfolioFundAndStatus(
                    fixture.portfolioFundId(), TransactionStatus.CONFIRMED);
            List<LedgerTransaction> all = transactions.findByPortfolioFundOrderByTradeDateDesc(
                    fixture.portfolioFundId());
            Lot lot = lots.findByPortfolioFund(fixture.portfolioFundId()).stream().findFirst().orElseThrow();
            List<String> redemptions = lots.findRedemptionsByLotIds(List.of(lot.id())).stream()
                    .map(redemption -> redemption.sellTransactionId() + ":"
                            + redemption.sharesConsumed().stripTrailingZeros().toPlainString())
                    .sorted().toList();
            return new Facts(
                    LedgerReplay.netShares(confirmed).stripTrailingZeros().toPlainString(),
                    lot.remainingShares().stripTrailingZeros().toPlainString(),
                    confirmed.stream().map(LedgerTransaction::id).sorted().toList(),
                    all.stream().filter(transaction -> transaction.status() == TransactionStatus.PENDING)
                            .map(LedgerTransaction::id).sorted().toList(),
                    redemptions);
        });
    }

    private MultiFundFacts readMultiFundFacts(MultiFundFixture fixture) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            List<LedgerTransaction> firstConfirmed = transactions.findByPortfolioFundAndStatus(
                    fixture.firstPortfolioFundId(), TransactionStatus.CONFIRMED);
            List<LedgerTransaction> secondConfirmed = transactions.findByPortfolioFundAndStatus(
                    fixture.secondPortfolioFundId(), TransactionStatus.CONFIRMED);
            List<Lot> firstLots = lots.findByPortfolioFund(fixture.firstPortfolioFundId());
            List<Lot> secondLots = lots.findByPortfolioFund(fixture.secondPortfolioFundId());
            List<Long> pendingIds = transactions.findByPortfolioFundIdsAndStatus(
                            List.of(fixture.firstPortfolioFundId(), fixture.secondPortfolioFundId()),
                            TransactionStatus.PENDING).stream()
                    .map(LedgerTransaction::id).sorted().toList();
            List<Long> lotIds = java.util.stream.Stream.concat(firstLots.stream(), secondLots.stream())
                    .map(Lot::id).toList();
            List<String> redemptions = lots.findRedemptionsByLotIds(lotIds).stream()
                    .map(redemption -> redemption.sellTransactionId() + ":"
                            + redemption.sharesConsumed().stripTrailingZeros().toPlainString())
                    .sorted().toList();
            return new MultiFundFacts(
                    netShares(firstConfirmed), netShares(secondConfirmed),
                    remainingShares(firstLots), remainingShares(secondLots), pendingIds, redemptions);
        });
    }

    private static String netShares(List<LedgerTransaction> transactions) {
        return LedgerReplay.netShares(transactions).stripTrailingZeros().toPlainString();
    }

    private static String remainingShares(List<Lot> lots) {
        return lots.stream().map(Lot::remainingShares).reduce(BigDecimal.ZERO, BigDecimal::add)
                .stripTrailingZeros().toPlainString();
    }

    private TransactionConfirmationFailure retryFailureOf(TransactionConfirmationCommandHandler confirmations,
                                                           long ownerId, long transactionId) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                confirmations.confirm(ownerId, transactionId);
                return null;
            });
            throw new AssertionError("失败的确认重试应仍被拒绝");
        } catch (TransactionConfirmationFailure failure) {
            return failure;
        }
    }

    private Fixture createFixture() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        long ownerId = users.ensureBootstrapAdmin("transaction-concurrency-" + UUID.randomUUID(),
                "integration-test-password").id();
        return new TransactionTemplate(transactionManager).execute(status -> {
            FundProductApi.ProductReference product = products.ensure(new FundProductApi.EnsureProduct(
                    "T" + suffix, "并发确认测试基金", null, null));
            FundEntity fund = new FundEntity();
            fund.setOwnerId(ownerId);
            fund.setProductId(product.id());
            fund.setFundCode(product.fundCode());
            fund.setFundName("并发确认测试基金");
            fund.setFundCategory(FundCategory.BROAD_BASE);
            fund.setFundSubType(FundSubType.INDEX);
            FundEntity savedFund = funds.save(fund);
            PortfolioFundApi.PortfolioFund portfolioFund = portfolioFunds.track(
                    new PortfolioFundApi.TrackPortfolioFund(savedFund.getId(), ownerId, product.id(),
                            true, new BigDecimal("0.30")));

            LedgerTransaction initial = transactions.save(LedgerTransaction.recordExistingPosition(
                    portfolioFund.id(), ownerId, new BigDecimal("10"), BigDecimal.ONE,
                    TRADE_DATE, TRADE_DATE));
            lots.save(Lot.open(portfolioFund.id(), initial.id(), TRADE_DATE,
                    new BigDecimal("10"), BigDecimal.ONE));
            LedgerTransaction first = transactions.save(LedgerTransaction.placePending(
                    portfolioFund.id(), ownerId, TransactionSource.DECREASE, null,
                    new BigDecimal("6"), TRADE_DATE, null, null));
            LedgerTransaction second = transactions.save(LedgerTransaction.placePending(
                    portfolioFund.id(), ownerId, TransactionSource.DECREASE, null,
                    new BigDecimal("6"), TRADE_DATE, null, null));
            return new Fixture(ownerId, product.id(), portfolioFund.id(), initial.id(), first.id(), second.id());
        });
    }

    private MultiFundFixture createMultiFundFixture(boolean includeReverseConversion) {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        long ownerId = users.ensureBootstrapAdmin("conversion-concurrency-" + UUID.randomUUID(),
                "integration-test-password").id();
        return new TransactionTemplate(transactionManager).execute(status -> {
            TrackedFund first = createTrackedFund(ownerId, "A" + suffix);
            TrackedFund second = createTrackedFund(ownerId, "B" + suffix);
            LedgerTransaction firstInitial = transactions.save(LedgerTransaction.recordExistingPosition(
                    first.portfolioFundId(), ownerId, new BigDecimal("10"), BigDecimal.ONE,
                    TRADE_DATE, TRADE_DATE));
            LedgerTransaction secondInitial = transactions.save(LedgerTransaction.recordExistingPosition(
                    second.portfolioFundId(), ownerId, new BigDecimal("10"), BigDecimal.ONE,
                    TRADE_DATE, TRADE_DATE));
            lots.save(Lot.open(first.portfolioFundId(), firstInitial.id(), TRADE_DATE,
                    new BigDecimal("10"), BigDecimal.ONE));
            lots.save(Lot.open(second.portfolioFundId(), secondInitial.id(), TRADE_DATE,
                    new BigDecimal("10"), BigDecimal.ONE));
            long forwardOutId = ledgerCommands.recordManual(ownerId, first.portfolioFundId(),
                    TransactionLedgerCommandHandler.Source.TRANSFER_OUT, null, new BigDecimal("6"),
                    TRADE_DATE, second.portfolioFundId()).transactionId();
            Long reverseOutId = includeReverseConversion
                    ? ledgerCommands.recordManual(ownerId, second.portfolioFundId(),
                            TransactionLedgerCommandHandler.Source.TRANSFER_OUT, null, new BigDecimal("6"),
                            TRADE_DATE, first.portfolioFundId()).transactionId()
                    : null;
            return new MultiFundFixture(ownerId, first.productId(), second.productId(),
                    first.portfolioFundId(), second.portfolioFundId(), forwardOutId, reverseOutId);
        });
    }

    private TrackedFund createTrackedFund(long ownerId, String suffix) {
        FundProductApi.ProductReference product = products.ensure(new FundProductApi.EnsureProduct(
                "T" + suffix, "多基金确认测试" + suffix, null, null));
        FundEntity fund = new FundEntity();
        fund.setOwnerId(ownerId);
        fund.setProductId(product.id());
        fund.setFundCode(product.fundCode());
        fund.setFundName("多基金确认测试" + suffix);
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setFundSubType(FundSubType.INDEX);
        FundEntity savedFund = funds.save(fund);
        long portfolioFundId = portfolioFunds.track(new PortfolioFundApi.TrackPortfolioFund(
                savedFund.getId(), ownerId, product.id(), true, new BigDecimal("0.30"))).id();
        return new TrackedFund(product.id(), portfolioFundId);
    }

    private SettlementFeeGateway feeGateway(MultiFundFixture fixture) {
        SettlementFeeGateway fees = mock(SettlementFeeGateway.class);
        when(fees.feeScheduleOf(fixture.firstProductId())).thenReturn(FeeSchedule.none());
        when(fees.feeScheduleOf(fixture.secondProductId())).thenReturn(FeeSchedule.none());
        return fees;
    }

    private SettlementNavGateway navGateway(MultiFundFixture fixture, AtomicBoolean targetNavAvailable) {
        SettlementNavGateway navs = mock(SettlementNavGateway.class);
        when(navs.unitNavOn(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenAnswer(invocation -> {
                    long productId = invocation.getArgument(0);
                    return productId == fixture.secondProductId() && !targetNavAvailable.get()
                            ? Optional.empty() : Optional.of(BigDecimal.ONE);
                });
        return navs;
    }

    private TransactionConfirmationCommandHandler confirmations(TradedPortfolioFundGateway portfolioFunds,
                                                                 SettlementFeeGateway fees,
                                                                 SettlementNavGateway navs) {
        return new TransactionConfirmationCommandHandler(transactions, lots, portfolioFunds, fees, navs,
                mock(PositionCommandHandler.class), mock(LedgerEventGateway.class),
                mock(RequiresNewTransactionExecutor.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TransactionConfirmationFailure failureOf(java.util.concurrent.Future<?> future)
            throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
            throw new AssertionError("第二笔并发确认应被拒绝");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof TransactionConfirmationFailure failure) {
                return failure;
            }
            throw exception;
        }
    }

    private record Fixture(long ownerId, long fundProductId, long portfolioFundId,
                           long initialTransactionId, long firstPendingId, long secondPendingId) {
    }

    private record Facts(String netShares, String remainingLotShares, List<Long> confirmedIds,
                         List<Long> pendingIds, List<String> redemptions) {
    }

    private record TrackedFund(long productId, long portfolioFundId) {
    }

    private record MultiFundFixture(long ownerId, long firstProductId, long secondProductId,
                                    long firstPortfolioFundId, long secondPortfolioFundId,
                                    long forwardOutId, Long reverseOutId) {
    }

    private record MultiFundFacts(String firstNetShares, String secondNetShares,
                                  String firstLotShares, String secondLotShares,
                                  List<Long> pendingIds, List<String> redemptions) {
    }

    private static final class LockGate implements TradedPortfolioFundGateway {
        private final TradedPortfolioFundGateway delegate;
        private final AtomicInteger attempts = new AtomicInteger();
        private final CountDownLatch firstLocked = new CountDownLatch(1);
        private final CountDownLatch secondAttempted = new CountDownLatch(1);
        private final CountDownLatch secondLockReturned = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);

        private LockGate(TradedPortfolioFundGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<TradedPortfolioFund> find(long portfolioFundId) {
            return delegate.find(portfolioFundId);
        }

        @Override
        public Optional<TradedPortfolioFund> findForUpdate(long portfolioFundId) {
            int attempt = attempts.incrementAndGet();
            if (attempt == 2) {
                secondAttempted.countDown();
            }
            Optional<TradedPortfolioFund> result = delegate.findForUpdate(portfolioFundId);
            if (attempt == 1) {
                firstLocked.countDown();
                await(releaseFirst);
            }
            if (attempt == 2) {
                secondLockReturned.countDown();
            }
            return result;
        }

        @Override
        public Optional<TradedPortfolioFund> findOwned(long ownerId, long portfolioFundId) {
            return delegate.findOwned(ownerId, portfolioFundId);
        }

        @Override
        public List<TradedPortfolioFund> findTradableByOwner(long ownerId) {
            return delegate.findTradableByOwner(ownerId);
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

    private static final class MultiFundLockGate implements TradedPortfolioFundGateway {
        private final TradedPortfolioFundGateway delegate;
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicLong firstPortfolioFundId = new AtomicLong();
        private final AtomicLong secondPortfolioFundId = new AtomicLong();
        private final CountDownLatch firstLocked = new CountDownLatch(1);
        private final CountDownLatch secondAttempted = new CountDownLatch(1);
        private final CountDownLatch secondLockReturned = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);

        private MultiFundLockGate(TradedPortfolioFundGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<TradedPortfolioFund> find(long portfolioFundId) {
            return delegate.find(portfolioFundId);
        }

        @Override
        public Optional<TradedPortfolioFund> findForUpdate(long portfolioFundId) {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                firstPortfolioFundId.set(portfolioFundId);
            } else if (attempt == 2) {
                secondPortfolioFundId.set(portfolioFundId);
                secondAttempted.countDown();
            }
            Optional<TradedPortfolioFund> result = delegate.findForUpdate(portfolioFundId);
            if (attempt == 1) {
                firstLocked.countDown();
                LockGate.await(releaseFirst);
            } else if (attempt == 2) {
                secondLockReturned.countDown();
            }
            return result;
        }

        @Override
        public Optional<TradedPortfolioFund> findOwned(long ownerId, long portfolioFundId) {
            return delegate.findOwned(ownerId, portfolioFundId);
        }

        @Override
        public List<TradedPortfolioFund> findTradableByOwner(long ownerId) {
            return delegate.findTradableByOwner(ownerId);
        }
    }
}
