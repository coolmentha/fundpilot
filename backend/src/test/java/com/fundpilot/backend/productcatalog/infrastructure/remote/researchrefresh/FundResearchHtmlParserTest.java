package com.fundpilot.backend.productcatalog.infrastructure.remote.researchrefresh;

import com.fundpilot.backend.productcatalog.domain.research.FundResearch.HoldingKind;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.ShareClass;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FundResearchHtmlParserTest {
    private static final String A_PROFILE = """
            <table>
              <tr><th>基金简称</th><td>天弘中证500ETF联接A</td></tr>
              <tr><th>基金类型</th><td>指数型-股票</td></tr>
              <tr><th>发行日期</th><td>2018年04月24日</td></tr>
              <tr><th>跟踪标的</th><td>000905 中证500指数</td></tr>
              <tr><th>目标ETF</th><td>510500 南方中证500ETF</td></tr>
              <tr><th>更新日期</th><td>2026-06-30</td></tr>
            </table>
            """;
    private static final String C_PROFILE = """
            <table><tr><th>基金简称</th><td>天弘中证500ETF联接C</td></tr>
            <tr><th>基金类型</th><td>指数型-股票</td></tr></table>
            """;

    @Test
    void parsesOrdinaryAAndCProfilesWithoutInventingMissingReferences() {
        var a = FundResearchHtmlParser.parseProfile(A_PROFILE);
        var c = FundResearchHtmlParser.parseProfile(C_PROFILE);

        assertThat(a.data().shareClass()).isEqualTo(ShareClass.A);
        assertThat(a.data().trackingIndex().code()).isEqualTo("000905");
        assertThat(a.data().targetEtf().code()).isEqualTo("510500");
        assertThat(a.data().launchDate()).isEqualTo(Instant.parse("2018-04-24T00:00:00Z"));
        assertThat(a.reportDate()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
        assertThat(c.data().shareClass()).isEqualTo(ShareClass.C);
        assertThat(c.data().trackingIndex()).isNull();
        assertThat(c.data().targetEtf()).isNull();
    }

    @Test
    void issuanceDateDoesNotPretendToBeReportDate() {
        var parsed = FundResearchHtmlParser.parseProfile("""
                <table><tr><th>基金简称</th><td>测试基金A</td></tr>
                <tr><th>发行日期</th><td>2020-01-02</td></tr></table>
                """);

        assertThat(parsed.data().launchDate()).isEqualTo(Instant.parse("2020-01-02T00:00:00Z"));
        assertThat(parsed.reportDate()).isNull();
    }

    @Test
    void parsesCategoryAndCombinedScaleWithoutConflatingThem() {
        var parsed = FundResearchHtmlParser.parseScale("""
                <table><tr><th>报告日期</th><th>基金份额（亿份）</th>
                <th>期末净资产（亿元）</th><th>合并资产规模（亿元）</th></tr>
                <tr><td>2026-06-30</td><td>12.34</td><td>56.78</td><td>98.76</td></tr></table>
                """);

        assertThat(parsed.data().shareScale().value()).isEqualByComparingTo("12.34");
        assertThat(parsed.data().categoryAssetScale().value()).isEqualByComparingTo("56.78");
        assertThat(parsed.data().combinedAssetScale().value()).isEqualByComparingTo("98.76");
        assertThat(parsed.reportDate()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
        assertThat(FundResearchHtmlParser.parseScale("<table><tr><td>结构已变化</td></tr></table>"))
                .isNull();
    }

    @Test
    void parsesRealSampleLatestQuarterOnlyAcrossHeaderChanges() throws IOException {
        var parsed = FundResearchHtmlParser.parseHoldings(loadHoldingsSample());
        var stocks = parsed.data().stockHoldings().stream()
                .filter(item -> item.kind() == HoldingKind.STOCK).toList();

        assertThat(parsed.reportDate()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
        assertThat(stocks).extracting(item -> item.code())
                .containsExactly("00700", "600519", "920136")
                .doesNotContain("000858", "09987");
        assertThat(stocks.getFirst().name()).isEqualTo("腾讯控股");
        assertThat(stocks.getLast().weight()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void keepsTopHoldingsCoverageAndUnknownInsteadOfNormalizing() {
        var parsed = FundResearchHtmlParser.parseHoldings("""
                <div>报告期：2026-06-30</div>
                <table data-kind="股票持仓"><tr><th>代码</th><th>名称</th><th>占比</th></tr>
                <tr><td>600000</td><td>浦发银行</td><td>12.00%</td></tr>
                <tr><td>000001</td><td>平安银行</td><td>8.00%</td></tr></table>
                <table data-kind="行业"><tr><td>金融</td><td>20.00%</td></tr></table>
                """);

        assertThat(parsed.data().disclosedCoverage()).isEqualByComparingTo(new BigDecimal("0.2"));
        assertThat(parsed.data().stockHoldings()).hasSize(3);
        assertThat(parsed.data().stockHoldings().get(2).kind()).isEqualTo(HoldingKind.UNKNOWN);
        assertThat(parsed.data().stockHoldings().get(2).weight()).isEqualByComparingTo("0.8");
        assertThat(parsed.data().regionHoldings()).isNull();
        assertThat(parsed.data().currencyHoldings()).isNull();
    }

    @Test
    void addsPublishedCashWithoutInventingTargetEtfOrIndustryWeights() {
        var base = FundResearchHtmlParser.parseHoldings("""
                <div class="box"><div class="boxitem w790">
                <h4 class="t"><label>2026年2季度股票投资明细</label>
                <label>截止至：<font>2026-06-30</font></label></h4>
                <table><tr><td>600000</td><td>浦发银行</td><td>1.00%</td></tr></table>
                </div></div>
                """);
        String allocation = """
                {"Datas":[{"FSRQ":"2026-06-30","GP":"1.00","HB":"5.00","JJ":"90.00"}]}
                """;

        assertThat(base.reportDate()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));

        var parsed = FundResearchHtmlParser.addAllocation(base, allocation);

        assertThat(parsed.data().stockHoldings()).extracting(item -> item.kind())
                .containsExactly(HoldingKind.STOCK, HoldingKind.CASH, HoldingKind.UNKNOWN);
        assertThat(parsed.data().stockHoldings().get(1).weight()).isEqualByComparingTo("0.05");
        assertThat(parsed.data().stockHoldings().get(2).weight()).isEqualByComparingTo("0.94");
        assertThat(parsed.data().industryHoldings()).isNull();
        assertThat(parsed.reportDate()).isEqualTo(base.reportDate());
    }

    @Test
    void allocationFromDifferentReportDateDoesNotChangeBase() {
        var base = FundResearchHtmlParser.parseHoldings("""
                <div class="box"><div class="boxitem w790">
                <h4 class="t"><label>2025年4季度股票投资明细</label>
                <label>截止至：<font>2025-12-31</font></label></h4>
                <table><tr><td>00700</td><td>腾讯控股</td><td>5.72%</td></tr></table>
                </div></div>
                """);

        assertThat(base.reportDate()).isEqualTo(Instant.parse("2025-12-31T00:00:00Z"));

        var parsed = FundResearchHtmlParser.addAllocation(base,
                "{\"Datas\":[{\"FSRQ\":\"2026-06-30\",\"HB\":\"5.00\"}]}");

        assertThat(parsed).isEqualTo(base);
        assertThat(parsed.data().stockHoldings())
                .noneMatch(item -> item.kind() == HoldingKind.CASH);
    }

    @Test
    void missingOrInvalidAllocationDateDoesNotChangeBase() {
        var base = FundResearchHtmlParser.parseHoldings("""
                <div class="box"><div class="boxitem w790">
                <h4 class="t"><label>2026年2季度股票投资明细</label>
                <label>截止至：<font>2026-06-30</font></label></h4>
                <table><tr><td>00700</td><td>腾讯控股</td><td>5.72%</td></tr></table>
                </div></div>
                """);

        assertThat(base.reportDate()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));

        assertThat(FundResearchHtmlParser.addAllocation(base,
                "{\"Datas\":[{\"HB\":\"5.00\"}]}"))
                .isEqualTo(base);
        assertThat(FundResearchHtmlParser.addAllocation(base,
                "{\"Datas\":[{\"FSRQ\":\"2026-02-30\",\"HB\":\"5.00\"}]}"))
                .isEqualTo(base);
    }

    @Test
    void totalFundInvestmentRatioDoesNotBecomeSpecificTargetEtfWeight() {
        var parsed = FundResearchHtmlParser.parseHoldings("""
                <div>报告期：2026-06-30</div>
                <table data-kind="股票持仓"><tr><td>600000</td><td>浦发银行</td><td>1.00%</td></tr></table>
                <table data-kind="基金投资"><tr><th>基金投资占基金总资产的比例</th><td>94.24%</td></tr></table>
                """);

        assertThat(parsed.data().stockHoldings())
                .noneMatch(item -> item.kind() == HoldingKind.TARGET_ETF);
    }

    @Test
    void missingTargetEtfCodeRemainsUnknown() {
        var profile = FundResearchHtmlParser.parseProfile("""
                <table><tr><th>基金简称</th><td>测试联接基金C</td></tr>
                <tr><th>跟踪指数</th><td>000905 中证500指数</td></tr></table>
                """);

        var parsed = FundResearchHtmlParser.addTargetEtf(profile,
                "{\"Datas\":[{\"JJ\":\"94.24\",\"GP\":\"0.00\"}]}");

        assertThat(parsed.data().trackingIndex().code()).isEqualTo("000905");
        assertThat(parsed.data().targetEtf()).isNull();
    }

    @Test
    void unavailableOptionalTargetPayloadDoesNotEraseKnownProfile() {
        var profile = FundResearchHtmlParser.parseProfile(C_PROFILE);

        var parsed = FundResearchHtmlParser.addTargetEtf(profile, null);

        assertThat(parsed).isNotNull();
        assertThat(parsed.data().shareClass()).isEqualTo(ShareClass.C);
        assertThat(parsed.data().targetEtf()).isNull();
    }

    @Test
    void missingOptionalAllocationFieldsDoNotErasePublishedHoldings() {
        var holdings = FundResearchHtmlParser.parseHoldings("""
                <div class="box"><div class="boxitem w790">
                <h4 class="t"><label>2026年2季度股票投资明细</label>
                <label>截止至：<font>2026-06-30</font></label></h4>
                <table><tr><td>00700</td><td>腾讯控股</td><td>5.72%</td></tr></table>
                </div></div>
                """);

        assertThat(holdings.reportDate()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));

        var parsed = FundResearchHtmlParser.addAllocation(holdings, "{}");

        assertThat(parsed).isNotNull();
        assertThat(parsed.data().stockHoldings()).extracting(item -> item.code()).contains("00700");
    }

    @Test
    void parsesLatestIndependentIndustryJsonAndKeepsPublishedZero() {
        var parsed = FundResearchHtmlParser.parseIndustry("""
                {"Data":{"QuarterInfos":[
                  {"JZRQ":"2026-03-31","HYPZInfo":[{"HYMC":"金融","ZJZBL":"20.00"}]},
                  {"JZRQ":"2026-06-30","HYPZInfo":[
                    {"HYMC":"制造业","ZJZBL":"35.50"},
                    {"HYMC":"零值类别","ZJZBL":"0.00"},
                    {"HYMC":"缺失比例"},{"ZJZBL":"12.00"}
                  ]}
                ]}}
                """);

        assertThat(parsed.reportDate()).isEqualTo(Instant.parse("2026-06-30T00:00:00Z"));
        assertThat(parsed.data().holdings()).extracting(item -> item.name())
                .containsExactly("制造业", "零值类别");
        assertThat(parsed.data().holdings().getLast().weight()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private String loadHoldingsSample() throws IOException {
        try (var input = getClass().getResourceAsStream(
                "/productcatalog/researchrefresh/holdings-data-sample.html")) {
            if (input == null) throw new IllegalStateException("研究持仓测试夹具不存在");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
