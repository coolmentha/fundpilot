package com.fundpilot.backend.productcatalog.infrastructure.remote.researchrefresh;

import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EastmoneyFundResearchSourceGatewayTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void fetchesIndependentProfileScaleAndIndustrySources() {
        var client = mock(EastmoneyFundResearchClient.class);
        var mobile = mock(EastmoneyFundResearchMobileClient.class);
        var industry = mock(EastmoneyFundResearchIndustryClient.class);
        when(client.fetchProfile("005919")).thenReturn("""
                <table><tr><th>基金简称</th><td>天弘中证500ETF联接C</td></tr>
                <tr><th>发行日期</th><td>2018-04-24</td></tr></table>
                """);
        when(mobile.fetchPosition("005919"))
                .thenReturn("{\"ETFCODE\":\"510500\",\"ETFSHORTNAME\":\"中证500ETF\"}");
        when(client.fetchScale("005919")).thenReturn("""
                <table><tr><th>报告日期</th><th>期末净资产</th><th>合并资产规模</th></tr>
                <tr><td>2026-06-30</td><td>12.30</td><td>45.60</td></tr></table>
                """);
        when(industry.fetchIndustry("005919", 2026)).thenReturn("暂无行业配置");
        when(industry.fetchIndustry("005919", 2025)).thenReturn("""
                {"Data":{"QuarterInfos":[{"JZRQ":"2025-12-31",
                "HYPZInfo":[{"HYMC":"制造业","ZJZBL":"35.50"}]}]}}
                """);
        var gateway = new EastmoneyFundResearchSourceGateway(client, mobile, industry, CLOCK);

        var profile = gateway.fetchProfile("005919");
        var scale = gateway.fetchScale("005919");
        var allocation = gateway.fetchIndustry("005919");

        assertThat(profile.data().targetEtf().code()).isEqualTo("510500");
        assertThat(profile.reportDate()).isNull();
        assertThat(profile.source().name()).isEqualTo("东方财富");
        assertThat(profile.source().url()).endsWith("jbgk_005919.html");
        assertThat(scale.data().categoryAssetScale().value()).isEqualByComparingTo("12.30");
        assertThat(scale.data().combinedAssetScale().value()).isEqualByComparingTo("45.60");
        assertThat(allocation.data().holdings()).extracting(item -> item.name()).containsExactly("制造业");
        assertThat(allocation.source().url()).contains("fundCode=005919&year=2025");
    }

    @Test
    void holdingsFallBackToPreviousYearWithoutMerging() {
        var client = mock(EastmoneyFundResearchClient.class);
        var mobile = mock(EastmoneyFundResearchMobileClient.class);
        var industry = mock(EastmoneyFundResearchIndustryClient.class);
        when(client.fetchHoldings("005827", 2026)).thenReturn("页面暂无持仓");
        when(client.fetchHoldings("005827", 2025)).thenReturn("""
                <div class="box"><h4>2025年4季度股票投资明细 截止至：2025-12-31</h4>
                <table><tr><th>股票代码</th><th>股票名称</th><th>占净值比例</th></tr>
                <tr><td>00700</td><td>腾讯控股</td><td>8.00%</td></tr></table></div>
                """);
        when(mobile.fetchAllocation("005827"))
                .thenReturn("{\"Datas\":[{\"FSRQ\":\"2025-12-31\",\"HB\":\"0.00\"}]}");
        var gateway = new EastmoneyFundResearchSourceGateway(client, mobile, industry, CLOCK);

        var result = gateway.fetchHoldings("005827");

        assertThat(result.reportDate()).isEqualTo(Instant.parse("2025-12-31T00:00:00Z"));
        assertThat(result.data().stockHoldings()).extracting(item -> item.code())
                .contains("00700").doesNotContain("000858");
        assertThat(result.source().url()).contains("year=2025");
        verify(client).fetchHoldings("005827", 2026);
        verify(client).fetchHoldings("005827", 2025);
    }

    @Test
    void allHoldingYearsUnavailableReturnNull() {
        var client = mock(EastmoneyFundResearchClient.class);
        var mobile = mock(EastmoneyFundResearchMobileClient.class);
        var industry = mock(EastmoneyFundResearchIndustryClient.class);
        when(client.fetchHoldings("005827", 2026)).thenReturn("页面暂无持仓");
        when(client.fetchHoldings("005827", 2025)).thenReturn("页面仍无持仓");
        when(mobile.fetchAllocation("005827")).thenReturn("{}");
        var gateway = new EastmoneyFundResearchSourceGateway(client, mobile, industry, CLOCK);

        assertThat(gateway.fetchHoldings("005827")).isNull();
        verify(client).fetchHoldings("005827", 2026);
        verify(client).fetchHoldings("005827", 2025);
    }

    @Test
    void nullOrBlankPositionMakesProfileCollectionFail() {
        var client = mock(EastmoneyFundResearchClient.class);
        var mobile = mock(EastmoneyFundResearchMobileClient.class);
        when(client.fetchProfile("005919")).thenReturn("""
                <table><tr><th>基金简称</th><td>天弘中证500ETF联接C</td></tr></table>
                """);
        when(mobile.fetchPosition("005919")).thenReturn(null, " ");
        var gateway = new EastmoneyFundResearchSourceGateway(client, mobile,
                mock(EastmoneyFundResearchIndustryClient.class), CLOCK);

        assertThat(gateway.fetchProfile("005919")).isNull();
        assertThat(gateway.fetchProfile("005919")).isNull();
    }

    @Test
    void nullOrBlankAllocationMakesHoldingsCollectionFail() {
        var client = mock(EastmoneyFundResearchClient.class);
        var mobile = mock(EastmoneyFundResearchMobileClient.class);
        when(client.fetchHoldings("005827", 2026)).thenReturn("""
                <div class="box"><h4>2026年2季度股票投资明细 截止至：2026-06-30</h4>
                <table><tr><th>股票代码</th><th>股票名称</th><th>占净值比例</th></tr>
                <tr><td>00700</td><td>腾讯控股</td><td>5.72%</td></tr></table></div>
                """);
        when(mobile.fetchAllocation("005827")).thenReturn(null, "");
        var gateway = new EastmoneyFundResearchSourceGateway(client, mobile,
                mock(EastmoneyFundResearchIndustryClient.class), CLOCK);

        assertThat(gateway.fetchHoldings("005827")).isNull();
        assertThat(gateway.fetchHoldings("005827")).isNull();
    }

    @Test
    void sourceTimeoutIsPropagatedToRefreshBoundary() {
        var client = mock(EastmoneyFundResearchClient.class);
        var gateway = new EastmoneyFundResearchSourceGateway(client,
                mock(EastmoneyFundResearchMobileClient.class),
                mock(EastmoneyFundResearchIndustryClient.class), CLOCK);
        Request request = Request.create(Request.HttpMethod.GET, "https://example.test",
                Map.of(), (byte[]) null, StandardCharsets.UTF_8, new RequestTemplate());
        var timeout = new RetryableException(0, "timeout", Request.HttpMethod.GET,
                new SocketTimeoutException("timeout"), (Long) null, request);
        when(client.fetchScale("005919")).thenThrow(timeout);

        assertThatThrownBy(() -> gateway.fetchScale("005919")).isSameAs(timeout);
    }
}
