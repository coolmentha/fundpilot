package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #6 验收:fundcode_search.js GraalVM JS 解析 → {@link FundDictEntry} 列表。
 * <p>固化样本模拟真实响应,验证 [fundCode, fundName, rawType] 三元组被正确提取。
 */
class EastmoneyJsParserFundDictTest {

    private static final String SAMPLE = """
            var r = [["000001","华夏成长混合","稳健成长型"],["000011","华夏大盘精选","积极成长型"],["110011","易方达中小盘","稳健成长型"]];
            """;

    @Test
    void parseFundDict() {
        List<FundDictEntry> entries = EastmoneyJsParser.parseFundDict(SAMPLE);

        assertThat(entries).hasSize(3);

        FundDictEntry first = entries.get(0);
        assertThat(first.fundCode()).isEqualTo("000001");
        assertThat(first.fundName()).isEqualTo("华夏成长混合");
        assertThat(first.rawName()).isEqualTo("稳健成长型");

        assertThat(entries.get(2).fundCode()).isEqualTo("110011");
    }

    @Test
    void parseFundDictFromEmptyArray() {
        assertThat(EastmoneyJsParser.parseFundDict("var r = [];")).isEmpty();
    }
}