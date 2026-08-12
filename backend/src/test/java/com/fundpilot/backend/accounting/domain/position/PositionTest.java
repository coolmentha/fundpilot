package com.fundpilot.backend.accounting.domain.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void ADJUST_IN零成本份额只稀释分母不参与分子加权() {
        Position position = Position.empty(11L, 3L);
        position.applyPurchase(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("1000"),
                BigDecimal.ZERO);
        assertThat(position.costPerShare()).isEqualByComparingTo("10.00");

        position.applyPurchase(new BigDecimal("300"), new BigDecimal("100"), new BigDecimal("1000"),
                new BigDecimal("100"));
        assertThat(position.costPerShare())
                .isCloseTo(new BigDecimal("6.6666666666666667"), within(new BigDecimal("1E-15")));
    }

    @Test
    void 无未跟踪份额时加权结果与旧公式一致() {
        Position position = Position.empty(11L, 3L);
        position.applyPurchase(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("1000"),
                BigDecimal.ZERO);
        position.applyPurchase(new BigDecimal("200"), new BigDecimal("100"), new BigDecimal("1500"),
                BigDecimal.ZERO);
        assertThat(position.costPerShare()).isEqualByComparingTo("12.50");
    }

    @Test
    void 修正当前成本价不改变其他持仓事实() {
        Instant openedAt = Instant.parse("2026-08-01T00:00:00Z");
        Position position = Position.empty(11L, 3L);
        position.reconcile(true, new BigDecimal("100"), openedAt);
        position.applyExistingPosition(new BigDecimal("1.10"), openedAt);

        position.correctCostPerShare(new BigDecimal("1.25"));

        assertThat(position.costPerShare()).isEqualByComparingTo("1.25");
        assertThat(position.status()).isEqualTo(PositionStatus.OPEN);
        assertThat(position.openedAt()).isEqualTo(openedAt);
    }

    @Test
    void 非当前持仓不能修正成本价() {
        Position position = Position.empty(11L, 3L);

        assertThatThrownBy(() -> position.correctCostPerShare(new BigDecimal("1.25")))
                .isInstanceOf(IllegalStateException.class);
    }
}
