package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #36 验收:fundgz 盘中估值解析。
 * <p>东方财富 fundgz 接口返回 {@code jsonpgz({...})} JSONP 包裹,剥外壳后是标准 JSON,
 * 含 gsz(估算单位净值)、gszzl(估算涨跌幅%)、gztime(估值时间)、jzrq(基准净值日期)。
 */
class EastmoneyJsParserFundGzTest {

    @Test
    void parseFundGz_正常响应_提取估算涨跌幅与时间() {
        String raw = "jsonpgz({\"fundcode\":\"008585\",\"name\":\"华夏人工智能ETF联接A\","
                + "\"jzrq\":\"2026-06-25\",\"dwjz\":\"1.9700\",\"gsz\":\"1.8790\","
                + "\"gszzl\":\"-4.62\",\"gztime\":\"2026-06-26 15:00\"});";

        FundEstimateSnapshot snapshot = EastmoneyJsParser.parseFundGz(raw);

        assertThat(snapshot).isNotNull();
        // gszzl=-4.62 是百分比,转小数 -0.0462
        assertThat(snapshot.estimatedChangePct()).isEqualByComparingTo(new BigDecimal("-0.0462"));
        assertThat(snapshot.estimateTime()).isEqualTo("2026-06-26 15:00");
        assertThat(snapshot.baseNavDate()).isEqualTo("2026-06-25");
    }

    @Test
    void parseFundGz_空响应_返回_null() {
        assertThat(EastmoneyJsParser.parseFundGz("")).isNull();
        assertThat(EastmoneyJsParser.parseFundGz(null)).isNull();
    }

    @Test
    void parseFundGz_缺字段_返回_null() {
        // 无 gszzl 字段,无法算估算涨跌,降级返 null
        String raw = "jsonpgz({\"fundcode\":\"008585\",\"jzrq\":\"2026-06-25\"});";
        assertThat(EastmoneyJsParser.parseFundGz(raw)).isNull();
    }
}
