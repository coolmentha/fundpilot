package com.fundpilot.backend.market.service.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecidFormatTest {

    @Test
    void 沪市后缀_SH_转前缀_1() {
        assertThat(SecidFormat.fromIndexCode("000300.SH")).contains("1.000300");
        assertThat(SecidFormat.fromIndexCode("000016.SH")).contains("1.000016");
    }

    @Test
    void 深市后缀_SZ_转前缀_0() {
        assertThat(SecidFormat.fromIndexCode("399006.SZ")).contains("0.399006");
    }

    @Test
    void 中证指数后缀_CSI_转前缀_2() {
        assertThat(SecidFormat.fromIndexCode("930713.CSI")).contains("2.930713");
        assertThat(SecidFormat.fromIndexCode("931865.csi")).contains("2.931865");
    }

    @Test
    void 小写后缀_也能识别() {
        assertThat(SecidFormat.fromIndexCode("000300.sh")).contains("1.000300");
    }

    @Test
    void 无后缀_返回_empty() {
        assertThat(SecidFormat.fromIndexCode("000300")).isEmpty();
    }

    @Test
    void 未知后缀_返回_empty() {
        assertThat(SecidFormat.fromIndexCode("000300.BJ")).isEmpty();
    }

    @Test
    void null_或空_返回_empty() {
        assertThat(SecidFormat.fromIndexCode(null)).isEmpty();
        assertThat(SecidFormat.fromIndexCode("")).isEmpty();
        assertThat(SecidFormat.fromIndexCode("   ")).isEmpty();
    }

    @Test
    void 无点分隔_返回_empty() {
        assertThat(SecidFormat.fromIndexCode("SH000300")).isEmpty();
    }
}
