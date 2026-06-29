package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #6 验收:fundcode_search.js GraalVM JS 解析 → {@link FundDictEntry} 列表。
 * <p>固化样本模拟真实响应(5 元组:[fundCode, 拼音缩写, fundName(中文), 类型描述, 拼音全称]),
 * 验证 {@code [0]} 代码、{@code [2]} 中文名、{@code [3]} 类型描述被正确提取。
 */
class EastmoneyJsParserFundDictTest {

    private static final String SAMPLE = """
            var r = [["000001","HXCZHH","华夏成长混合","混合型-灵活","HUAXIACHENGZHANGHUNHE"],["000011","HXDPJX","华夏大盘精选","混合型-灵活","HUAXIADAPANJINGXUAN"],["110011","YFZXP","易方达中小盘","混合型-灵活","YIFANGDAZHONGXIAOPAN"]];
            """;

    @Test
    void parseFundDict() {
        List<FundDictEntry> entries = EastmoneyJsParser.parseFundDict(SAMPLE);

        assertThat(entries).hasSize(3);

        FundDictEntry first = entries.get(0);
        assertThat(first.fundCode()).isEqualTo("000001");
        assertThat(first.fundName()).isEqualTo("华夏成长混合");
        assertThat(first.rawName()).isEqualTo("混合型-灵活");

        assertThat(entries.get(2).fundCode()).isEqualTo("110011");
    }

    @Test
    void parseFundDictFromEmptyArray() {
        assertThat(EastmoneyJsParser.parseFundDict("var r = [];")).isEmpty();
    }
}
