package com.fundpilot.backend.fund.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyParamStatusTest {

    @Test
    void pendingCalibrationUsesDraftProductLabel() {
        assertThat(StrategyParamStatus.PENDING_CALIBRATION.getLabel()).isEqualTo("草稿");
    }
}
