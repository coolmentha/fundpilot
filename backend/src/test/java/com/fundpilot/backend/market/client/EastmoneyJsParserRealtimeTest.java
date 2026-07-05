package com.fundpilot.backend.market.client;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行情工作台验收:push2 实时行情三接口解析。
 * <p>样本数据来自 2026-07-04 真实接口验证(见 research/eastmoney-push2-api.md)。
 * f2/f4 价格类字段 ÷100 缩放,f3 涨跌幅 ÷10000 返小数(契约要求小数,前端 ×100 显示),
 * f6/f62 金额类字段原值。
 */
class EastmoneyJsParserRealtimeTest {

    @Test
    void parseIndexRealtime_正常响应_缩放还原并回填secid() {
        String raw = """
                {"rc":0,"data":{"total":3,"diff":[
                  {"f2":404364,"f3":37,"f4":1474,"f6":1465563104853.7,"f12":"000001","f14":"上证指数"},
                  {"f2":484217,"f3":62,"f4":2987,"f6":942569894349.8,"f12":"000300","f14":"沪深300"}
                ]}}
                """;
        List<String> secids = List.of("1.000001", "1.000300", "0.399006");

        List<IndexRealtimeSnapshot> result = EastmoneyJsParser.parseIndexRealtime(raw, secids);

        assertThat(result).hasSize(2);
        IndexRealtimeSnapshot sh = result.get(0);
        assertThat(sh.secid()).isEqualTo("1.000001");
        assertThat(sh.name()).isEqualTo("上证指数");
        // f2=404364 ÷100 = 4043.64
        assertThat(sh.currentPrice()).isEqualByComparingTo(new BigDecimal("4043.64"));
        // f3=37 ÷10000 = 0.0037(小数,表 +0.37%;前端 signedPercent ×100 显示)
        assertThat(sh.changePct()).isEqualByComparingTo(new BigDecimal("0.0037"));
        // f4=1474 ÷100 = 14.74
        assertThat(sh.changeAmount()).isEqualByComparingTo(new BigDecimal("14.74"));
        // f6 原值
        assertThat(sh.turnover()).isEqualByComparingTo(new BigDecimal("1465563104853.7"));
    }

    @Test
    void parseIndexRealtime_空响应_返回空列表() {
        assertThat(EastmoneyJsParser.parseIndexRealtime("", List.of("1.000001"))).isEmpty();
        assertThat(EastmoneyJsParser.parseIndexRealtime(null, List.of())).isEmpty();
        assertThat(EastmoneyJsParser.parseIndexRealtime("{\"data\":{\"diff\":[]}}", List.of())).isEmpty();
    }

    @Test
    void parseSectorList_正常响应_涨跌幅缩放成交额原值() {
        String raw = """
                {"rc":0,"data":{"total":496,"diff":[
                  {"f3":-22,"f6":3858445826.0,"f12":"BK0420","f14":"航空机场","f62":-169532400.0},
                  {"f3":153,"f6":3312519373.0,"f12":"BK0421","f14":"铁路公路","f62":86878864.0}
                ]}}
                """;

        List<SectorSnapshot> result = EastmoneyJsParser.parseSectorList(raw);

        assertThat(result).hasSize(2);
        SectorSnapshot first = result.get(0);
        assertThat(first.sectorCode()).isEqualTo("BK0420");
        assertThat(first.sectorName()).isEqualTo("航空机场");
        // f3=-22 ÷10000 = -0.0022(小数,表 -0.22%)
        assertThat(first.changePct()).isEqualByComparingTo(new BigDecimal("-0.0022"));
        // f6 原值
        assertThat(first.turnover()).isEqualByComparingTo(new BigDecimal("3858445826.0"));
        // f62 主力净流入原值
        assertThat(first.mainforceNet()).isEqualByComparingTo(new BigDecimal("-169532400.0"));
    }

    @Test
    void parseSectorList_f62缺失_mainforceNet为null() {
        String raw = """
                {"data":{"diff":[{"f3":100,"f6":1000.0,"f12":"BK0001","f14":"测试板块"}]}}
                """;

        List<SectorSnapshot> result = EastmoneyJsParser.parseSectorList(raw);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).mainforceNet()).isNull();
    }

    @Test
    void parseSectorList_空响应_返回空列表() {
        assertThat(EastmoneyJsParser.parseSectorList("")).isEmpty();
        assertThat(EastmoneyJsParser.parseSectorList(null)).isEmpty();
        assertThat(EastmoneyJsParser.parseSectorList("{\"data\":{}}")).isEmpty();
    }

    @Test
    void parseNorthbound_正常响应_取最后一条的北向合计() {
        String raw = """
                {"rc":0,"data":{"s2n":[
                  "9:30,0.00,5200000.00,0.00,5200000.00,0.00",
                  "9:31,100000.00,5100000.00,200000.00,5000000.00,300000.00",
                  "15:00,500000.00,4700000.00,300000.00,4900000.00,800000.00"
                ]}}
                """;

        MoneyFlowSnapshot snapshot = EastmoneyJsParser.parseNorthbound(raw);

        assertThat(snapshot).isNotNull();
        // 最后一条 CSV 第 5 列(索引 5)= 800000.00
        assertThat(snapshot.northboundNet()).isEqualByComparingTo(new BigDecimal("800000.00"));
        assertThat(snapshot.snapshotTime()).isNotNull();
    }

    @Test
    void parseNorthbound_空响应_返回null() {
        assertThat(EastmoneyJsParser.parseNorthbound("")).isNull();
        assertThat(EastmoneyJsParser.parseNorthbound(null)).isNull();
        assertThat(EastmoneyJsParser.parseNorthbound("{\"data\":{\"s2n\":[]}}")).isNull();
    }
}
