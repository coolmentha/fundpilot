package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ThsJsParserTest {

    @Test
    void parseNavHistory_按日期关联单位和累计净值() {
        String unit = "var dwjz_510300=[[\"20260715\",\"4.8336\"],[\"20260714\",\"4.8010\"]];";
        String accumulated = "var ljjz_510300=[[\"20260714\",\"2.1030\"],[\"20260715\",\"2.1195\"]];";

        var result = ThsJsParser.parseNavHistory(unit, accumulated);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).navDate()).isEqualTo(Instant.parse("2026-07-14T00:00:00Z"));
        assertThat(result.get(0).accumulatedNav()).isEqualByComparingTo("2.1030");
        assertThat(result.get(1).nav()).isEqualByComparingTo("4.8336");
    }

    @Test
    void parseFundDict_解析_jsonp_对象字典() {
        String raw = """
                g({"data":{"info":{"count":2},"data":{
                  "f000001":{"code":"000001","name":"华夏成长混合","typename":"混合型-灵活"},
                  "f510300":{"code":"510300","name":"华泰柏瑞沪深300ETF","typename":"指数型-股票"}
                }}})
                """;

        var result = ThsJsParser.parseFundDict(raw);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new FundDictEntry("000001", "华夏成长混合", "混合型-灵活"));
        assertThat(result.get(1).fundCode()).isEqualTo("510300");
    }

    @Test
    void parseIndexKline_解析_callback_中的日线字符串() {
        String raw = """
                callback({"name":"沪深300","total":2,"data":"20260714,4800.10,4850.20,4780.30,4830.40,123456;20260715,4830.40,4870.50,4820.10,4860.20,234567"})
                """;

        IndexKline result = ThsJsParser.parseIndexKline(raw);

        assertThat(result.bars()).hasSize(2);
        assertThat(result.bars().get(0).open()).isEqualByComparingTo("4800.10");
        assertThat(result.bars().get(0).high()).isEqualByComparingTo("4850.20");
        assertThat(result.bars().get(0).low()).isEqualByComparingTo("4780.30");
        assertThat(result.bars().get(0).close()).isEqualByComparingTo("4830.40");
        assertThat(result.bars().get(0).volume()).isEqualTo(123456L);
    }

    @Test
    void parseIndexKline_total为零返回空结果() {
        assertThat(ThsJsParser.parseIndexKline("callback({\"total\":0,\"data\":\"\"})").bars()).isEmpty();
    }

    @Test
    void parseMarketLimitCounts_取分钟数组末项() {
        String raw = """
                {"zdt_data":{"zd_time":["14:59","15:00"],"ztzs":[25,42],"dtzs":[10,25]}}
                """;

        assertThat(ThsJsParser.parseMarketLimitCounts(raw)).isEqualTo(new MarketLimitCounts(42, 25));
    }

    @Test
    void parseMarketLimitCounts_数组长度不一致返回空() {
        String raw = """
                {"zdt_data":{"zd_time":["15:00"],"ztzs":[42],"dtzs":[10,25]}}
                """;

        assertThat(ThsJsParser.parseMarketLimitCounts(raw)).isNull();
    }
}
