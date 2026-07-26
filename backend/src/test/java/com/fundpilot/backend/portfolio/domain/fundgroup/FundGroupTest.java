package com.fundpilot.backend.portfolio.domain.fundgroup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundGroupTest {
    @Test
    void normalizesNameWithoutFrameworkDependencies() {
        FundGroup group = new FundGroup(1L, 2L, " 核心 ", 0);

        assertThat(group.name()).isEqualTo("核心");
        assertThat(group.normalizedKey()).isEqualTo("核心");
    }

    @Test
    void rejectsInvalidNameAndOrder() {
        assertThatThrownBy(() -> new FundGroup(null, 2L, " ", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FundGroup(null, 2L, "核心", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
