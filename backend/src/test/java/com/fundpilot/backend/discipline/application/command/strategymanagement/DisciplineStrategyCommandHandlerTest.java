package com.fundpilot.backend.discipline.application.command.strategymanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.discipline.application.gateway.strategymanagement.StrategyPortfolioFundGateway;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import com.fundpilot.backend.platform.web.error.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

class DisciplineStrategyCommandHandlerTest {
    @Test
    void createKeepsCurrentStrategyAndReturnsDraft() {
        DisciplineStrategyRepository strategies = mock(DisciplineStrategyRepository.class);
        StrategyPortfolioFundGateway funds = mock(StrategyPortfolioFundGateway.class);
        when(funds.requireTrackedByLegacyFund(7L, 11L))
                .thenReturn(new StrategyPortfolioFundGateway.PortfolioFund(21L));
        when(strategies.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new DisciplineStrategyCommandHandler(strategies, funds).create(7L, 11L, input());

        assertThat(result.status()).isEqualTo("PENDING_CALIBRATION");
        assertThat(result.takeProfitPhase()).isNull();
        verify(strategies, never()).findEffectiveByPortfolioFundId(21L);
        verify(funds).requireTrackedForUpdate(7L, 21L);
        verify(strategies).save(org.mockito.ArgumentMatchers.any(DisciplineStrategy.class));
    }

    @Test
    void rehydrateAllowsLegacyActivationAtOne() {
        var strategy = DisciplineStrategy.rehydrate(1L, 21L, 7L, "PENDING_CALIBRATION",
                BigDecimal.ONE, new BigDecimal("0.06"), new BigDecimal("0.50"),
                new BigDecimal("0.50"), new BigDecimal("0.20"), 10,
                "TEST", 1, false, null, null, null, null, null);

        assertThat(strategy.activation()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(strategy.presetCategory()).isEqualTo("TEST");
    }

    @Test
    void reactivatingEffectiveStrategyKeepsRunningCycle() {
        var strategy = DisciplineStrategy.create(21L, 7L, input().toDomain());
        Instant cooldownAt = Instant.parse("2026-09-05T00:00:00Z");
        strategy.activate();
        strategy.markTriggered(31L);

        strategy.activate();

        assertThat(strategy.takeProfitPhase()).hasToString("TRIGGERED");
        assertThat(strategy.triggeredAdviceId()).isEqualTo(31L);

        strategy.enterCooldown(cooldownAt);
        strategy.activate();

        assertThat(strategy.takeProfitPhase()).hasToString("COOLDOWN");
        assertThat(strategy.cooldownStartedAt()).isEqualTo(cooldownAt);
        assertThat(strategy.cycleStartedAt()).isNull();
    }

    @Test
    void createRejectsUnknownPresetCategory() {
        DisciplineStrategyRepository strategies = mock(DisciplineStrategyRepository.class);
        StrategyPortfolioFundGateway funds = mock(StrategyPortfolioFundGateway.class);
        when(funds.requireTrackedForUpdate(7L, 21L))
                .thenReturn(new StrategyPortfolioFundGateway.PortfolioFund(21L));
        when(strategies.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var input = new DisciplineStrategyCommandHandler.Input(
                new BigDecimal("0.15"), new BigDecimal("0.06"), new BigDecimal("0.50"),
                new BigDecimal("0.50"), new BigDecimal("0.20"), 10, "UNKNOWN", 1, false);

        assertThatThrownBy(() -> new DisciplineStrategyCommandHandler(strategies, funds)
                .createForPortfolioFund(7L, 21L, input))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("STRATEGY_PARAM_INVALID"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    void createRejectsAllOutOfRangeStrategyParameters(String ignored, DisciplineStrategyCommandHandler.Input input) {
        DisciplineStrategyRepository strategies = mock(DisciplineStrategyRepository.class);
        StrategyPortfolioFundGateway funds = mock(StrategyPortfolioFundGateway.class);
        when(funds.requireTrackedForUpdate(7L, 21L))
                .thenReturn(new StrategyPortfolioFundGateway.PortfolioFund(21L));

        assertThatThrownBy(() -> new DisciplineStrategyCommandHandler(strategies, funds)
                .createForPortfolioFund(7L, 21L, input))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("STRATEGY_PARAM_INVALID"));
    }

    private static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("activation zero", input("0", "0.06", "0.50", "0.50", "0.20", 10, "BROAD_BASE", 1, false)),
                Arguments.of("activation one", input("1", "0.06", "0.50", "0.50", "0.20", 10, "BROAD_BASE", 1, false)),
                Arguments.of("pullback zero", input("0.15", "0", "0.50", "0.50", "0.20", 10, "BROAD_BASE", 1, false)),
                Arguments.of("pullback one", input("0.15", "1", "0.50", "0.50", "0.20", 10, "BROAD_BASE", 1, false)),
                Arguments.of("harvest zero", input("0.15", "0.06", "0", "0.50", "0.20", 10, "BROAD_BASE", 1, false)),
                Arguments.of("harvest above one", input("0.15", "0.06", "1.01", "0.50", "0.20", 10, "BROAD_BASE", 1, false)),
                Arguments.of("minimum holding negative", input("0.15", "0.06", "0.50", "-0.01", "0.20", 10, "BROAD_BASE", 1, false)),
                Arguments.of("minimum holding one", input("0.15", "0.06", "0.50", "1", "0.20", 10, "BROAD_BASE", 1, false)),
                Arguments.of("maximum sell zero", input("0.15", "0.06", "0.50", "0.50", "0", 10, "BROAD_BASE", 1, false)),
                Arguments.of("maximum sell above one", input("0.15", "0.06", "0.50", "0.50", "1.01", 10, "BROAD_BASE", 1, false)),
                Arguments.of("cooldown negative", input("0.15", "0.06", "0.50", "0.50", "0.20", -1, "BROAD_BASE", 1, false)),
                Arguments.of("cooldown above limit", input("0.15", "0.06", "0.50", "0.50", "0.20", 251, "BROAD_BASE", 1, false)),
                Arguments.of("unknown preset version", input("0.15", "0.06", "0.50", "0.50", "0.20", 10, "BROAD_BASE", 2, false))
        );
    }

    private static DisciplineStrategyCommandHandler.Input input(String activation, String pullback, String harvest,
                                                                  String minimumHolding, String maxSingleSell,
                                                                  Integer cooldownDays, String category,
                                                                  Integer version, Boolean customized) {
        return new DisciplineStrategyCommandHandler.Input(new BigDecimal(activation), new BigDecimal(pullback),
                new BigDecimal(harvest), new BigDecimal(minimumHolding), new BigDecimal(maxSingleSell), cooldownDays,
                category, version, customized);
    }

    private static DisciplineStrategyCommandHandler.Input input() {
        return new DisciplineStrategyCommandHandler.Input(
                new BigDecimal("0.15"), new BigDecimal("0.06"), new BigDecimal("0.50"),
                new BigDecimal("0.50"), new BigDecimal("0.20"), 10,
                "BROAD_BASE", 1, false);
    }
}
