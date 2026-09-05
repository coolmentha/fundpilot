package com.fundpilot.backend.portfolio.application.command.fundtracking;

import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFund;
import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundRepository;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundVoidedEvent;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundTrackedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fundpilot.backend.portfolio.application.gateway.fundtracking.PortfolioFundEventGateway;

class PortfolioFundCommandHandlerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

    @Test
    void tracksProductForOwner() {
        InMemoryRepository repository = new InMemoryRepository();
        PortfolioFundCommandHandler handler = handler(repository);

        var result = handler.track(101L, 3L, 5L, true, new BigDecimal("0.30"));

        assertThat(result.ownerId()).isEqualTo(3L);
        assertThat(result.fundProductId()).isEqualTo(5L);
        assertThat(result.validity()).isEqualTo("TRACKED");
    }

    @Test
    void rejectsDuplicateTrackedProductForSameOwner() {
        InMemoryRepository repository = new InMemoryRepository();
        PortfolioFundCommandHandler handler = handler(repository);
        handler.track(101L, 3L, 5L, true, new BigDecimal("0.30"));

        assertThatThrownBy(() -> handler.track(102L, 3L, 5L, true, new BigDecimal("0.30")))
                .isInstanceOfSatisfying(PortfolioFundFailure.class, failure ->
                        assertThat(failure.code()).isEqualTo(
                                PortfolioFundFailure.Code.PORTFOLIO_FUND_ALREADY_TRACKED));
    }

    @Test
    void hidesOtherOwnersPortfolioFundDuringUpdate() {
        InMemoryRepository repository = new InMemoryRepository();
        PortfolioFundCommandHandler handler = handler(repository);
        long portfolioFundId = handler.track(101L, 3L, 5L, true, new BigDecimal("0.30")).id();

        assertThatThrownBy(() -> handler.configureWarning(
                4L, portfolioFundId, false, new BigDecimal("0.20")))
                .isInstanceOfSatisfying(PortfolioFundFailure.class, failure ->
                        assertThat(failure.code()).isEqualTo(
                                PortfolioFundFailure.Code.PORTFOLIO_FUND_NOT_FOUND));
    }

    @Test
    void configuresWarningForOwnedTrackedPortfolioFund() {
        InMemoryRepository repository = new InMemoryRepository();
        PortfolioFundCommandHandler handler = handler(repository);
        long portfolioFundId = handler.track(101L, 3L, 5L, true, new BigDecimal("0.30")).id();

        var result = handler.configureWarning(3L, portfolioFundId, false, new BigDecimal("0.25"));

        assertThat(result.id()).isEqualTo(portfolioFundId);
        assertThat(result.positionWarningEnabled()).isFalse();
        assertThat(result.positionWarningRatio()).isEqualByComparingTo("0.25");
    }

    @Test
    void rejectsMissingOrOutOfRangeWarningRatio() {
        InMemoryRepository repository = new InMemoryRepository();
        PortfolioFundCommandHandler handler = handler(repository);
        long portfolioFundId = handler.track(101L, 3L, 5L, true, new BigDecimal("0.30")).id();

        assertThatThrownBy(() -> handler.configureWarning(3L, portfolioFundId, true, null))
                .isInstanceOfSatisfying(PortfolioFundFailure.class, failure ->
                        assertThat(failure.code()).isEqualTo(
                                PortfolioFundFailure.Code.POSITION_WARNING_INVALID));
        assertThatThrownBy(() -> handler.configureWarning(3L, portfolioFundId, true, BigDecimal.ONE.add(BigDecimal.ONE)))
                .isInstanceOfSatisfying(PortfolioFundFailure.class, failure ->
                        assertThat(failure.code()).isEqualTo(
                                PortfolioFundFailure.Code.POSITION_WARNING_INVALID));
    }

    @Test
    void publishesCompleteVoidedEventOnlyForFirstVoid() {
        InMemoryRepository repository = new InMemoryRepository();
        List<Object> published = new ArrayList<>();
        PortfolioFundCommandHandler handler = new PortfolioFundCommandHandler(
                repository, id -> true, recordingGateway(published), Clock.fixed(NOW, ZoneOffset.UTC));
        long portfolioFundId = handler.track(
                101L, 3L, 5L, true, new BigDecimal("0.30")).id();
        published.clear();
        Instant occurredAt = Instant.parse("2026-07-26T10:00:00Z");

        handler.voidPortfolioFund(3L, portfolioFundId, 7L, " 代码录入错误 ", occurredAt);
        handler.voidPortfolioFund(3L, portfolioFundId, 8L, "不同原因", occurredAt.plusSeconds(60));

        assertThat(published).containsExactly(new PortfolioFundVoidedEvent(
                portfolioFundId, 3L, 5L, 7L, "代码录入错误", occurredAt));
    }

    @Test
    void publishesTrackedEventAfterPersistence() {
        InMemoryRepository repository = new InMemoryRepository();
        List<Object> published = new ArrayList<>();
        PortfolioFundCommandHandler handler = new PortfolioFundCommandHandler(
                repository, id -> true, recordingGateway(published), Clock.fixed(NOW, ZoneOffset.UTC));

        long portfolioFundId = handler.track(
                101L, 3L, 5L, true, new BigDecimal("0.30")).id();

        assertThat(published).containsExactly(new PortfolioFundTrackedEvent(
                portfolioFundId, 3L, 5L, NOW));
    }

    /** 记录集成事件的测试网关,替代直接持有 Spring 事件发布器。 */
    private static PortfolioFundEventGateway recordingGateway(List<Object> published) {
        return new PortfolioFundEventGateway() {
            @Override public void publishTracked(PortfolioFundTrackedEvent event) { published.add(event); }
            @Override public void publishVoided(PortfolioFundVoidedEvent event) { published.add(event); }
        };
    }

    private static final class InMemoryRepository implements PortfolioFundRepository {
        private final List<PortfolioFund> portfolioFunds = new ArrayList<>();
        private long nextId = 1;

        @Override
        public Optional<PortfolioFund> findById(long id) {
            return portfolioFunds.stream().filter(item -> item.id() == id).findFirst();
        }

        @Override
        public Optional<PortfolioFund> findByIdForUpdate(long id) {
            return findById(id);
        }

        @Override
        public Optional<PortfolioFund> findTrackedByOwnerIdAndFundProductId(long ownerId,
                                                                            long fundProductId) {
            return portfolioFunds.stream()
                    .filter(item -> item.ownerId() == ownerId
                            && item.fundProductId() == fundProductId
                            && item.validity().name().equals("TRACKED"))
                    .findFirst();
        }

        @Override
        public Optional<PortfolioFund> saveTrackedIfAbsent(PortfolioFund portfolioFund) {
            if (findTrackedByOwnerIdAndFundProductId(portfolioFund.ownerId(), portfolioFund.fundProductId())
                    .isPresent()) {
                return Optional.empty();
            }
            return Optional.of(save(portfolioFund));
        }

        @Override
        public Optional<PortfolioFund> findByLegacyFundId(long legacyFundId) {
            return portfolioFunds.stream()
                    .filter(item -> item.legacyFundId() != null && item.legacyFundId() == legacyFundId)
                    .findFirst();
        }

        @Override
        public List<PortfolioFund> findByOwnerId(long ownerId) {
            return portfolioFunds.stream().filter(item -> item.ownerId() == ownerId).toList();
        }

        @Override
        public List<PortfolioFund> findAllTracked() {
            return portfolioFunds.stream().filter(item -> item.validity().name().equals("TRACKED")).toList();
        }

        @Override
        public PortfolioFund save(PortfolioFund portfolioFund) {
            if (portfolioFund.id() != null) return portfolioFund;
            PortfolioFund saved = PortfolioFund.rehydrate(nextId++, portfolioFund.legacyFundId(), portfolioFund.ownerId(),
                    portfolioFund.fundProductId(), portfolioFund.validity(),
                    portfolioFund.positionWarningEnabled(), portfolioFund.positionWarningRatio(),
                    portfolioFund.voidedAt(), portfolioFund.voidedBy(), portfolioFund.voidReason());
            portfolioFunds.add(saved);
            return saved;
        }
    }

    private PortfolioFundCommandHandler handler(InMemoryRepository repository) {
        return new PortfolioFundCommandHandler(repository, id -> true,
                recordingGateway(new ArrayList<>()), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
