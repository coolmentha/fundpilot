package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EastmoneyFundEstimatePageParserTest {

    @Test
    void parse_提取日期代码和百分比估算涨跌() {
        Map<String, FundEstimatePageRow> result = EastmoneyFundEstimatePageParser.parse("""
                <div id="gsdata">2026-08-07 估算数据</div>
                <div id="dwjzdata">2026-08-06</div>
                <table id="tContent"><tbody id="tableContent">
                  <tr><td></td><td>1</td><td>000001</td><td>测试基金</td>
                    <td data-gz="1.2345">--</td><td data-gz="-4.62%">--</td>
                    <td>---</td><td>---</td><td>---</td><td>1.3000</td><td></td>
                  </tr>
                </tbody></table>
                """);

        assertThat(result).containsKey("000001");
        assertThat(result.get("000001").estimatedChangePct()).isEqualByComparingTo(new BigDecimal("-0.0462"));
        assertThat(result.get("000001").estimateDate()).isEqualTo("2026-08-07");
        assertThat(result.get("000001").baseNavDate()).isEqualTo("2026-08-06");
    }

    @Test
    void parse_空页或404页面返回空结果() {
        assertThat(EastmoneyFundEstimatePageParser.parse(null)).isEmpty();
        assertThat(EastmoneyFundEstimatePageParser.parse("<html><title>页面未找到</title></html>")).isEmpty();
    }

    @Test
    void parse_表格缺少日期时标记结构解析失败() {
        assertThatThrownBy(() -> EastmoneyFundEstimatePageParser.parse(
                "<table id='tContent'><tbody id='tableContent'></tbody></table>"))
                .isInstanceOf(IllegalStateException.class);
    }
}
