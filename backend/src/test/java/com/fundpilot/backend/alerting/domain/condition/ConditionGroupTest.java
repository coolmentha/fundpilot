package com.fundpilot.backend.alerting.domain.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionGroupTest {

    @Test
    void 全部满足为默认组合方式() {
        AlertCondition condition = AlertCondition.of(IndicatorCode.INDEX_PE, ConditionRelation.BELOW);

        ConditionGroup group = ConditionGroup.single(condition);

        assertThat(group.match()).isEqualTo(AlertConditionMatch.ALL);
        assertThat(group.conditions()).containsExactly(condition);
    }

    @Test
    void 至少需要一条条件() {
        assertThatIllegalArgumentException().isThrownBy(() -> ConditionGroup.allOf(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> ConditionGroup.allOf(null));
    }

    @Test
    void 组合方式不能为空() {
        AlertCondition condition = AlertCondition.of(IndicatorCode.INDEX_PE, ConditionRelation.BELOW);

        assertThatIllegalArgumentException().isThrownBy(() -> new ConditionGroup(null, List.of(condition)));
    }

    @Test
    void 条件列表不可变() {
        AlertCondition condition = AlertCondition.of(IndicatorCode.INDEX_PE, ConditionRelation.BELOW);
        List<AlertCondition> mutable = new ArrayList<>(List.of(condition));
        ConditionGroup group = ConditionGroup.allOf(mutable);

        mutable.clear();

        assertThat(group.conditions()).containsExactly(condition);
        assertThatThrownBy(() -> group.conditions().add(condition)).isInstanceOf(UnsupportedOperationException.class);
    }
}