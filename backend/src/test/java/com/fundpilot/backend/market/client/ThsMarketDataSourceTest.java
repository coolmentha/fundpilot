package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThsMarketDataSourceTest {

    @Test
    void toInternalCode_覆盖沪深和中证格式() {
        assertThat(ThsMarketDataSource.toInternalCode("1.000001")).isEqualTo("hs_1A0001");
        assertThat(ThsMarketDataSource.toInternalCode("000300.SH")).isEqualTo("hs_1B0300");
        assertThat(ThsMarketDataSource.toInternalCode("0.399006")).isEqualTo("hs_399006");
        assertThat(ThsMarketDataSource.toInternalCode("2.930713")).isEqualTo("120_930713");
        assertThat(ThsMarketDataSource.toInternalCode("H30590.CSI")).isEqualTo("120_H30590");
    }
}
