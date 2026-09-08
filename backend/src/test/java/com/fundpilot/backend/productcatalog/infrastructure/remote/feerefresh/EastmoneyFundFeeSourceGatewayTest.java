package com.fundpilot.backend.productcatalog.infrastructure.remote.feerefresh;

import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule.PurchaseStatus;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EastmoneyFundFeeSourceGatewayTest {

    @Test
    void mapsPublishedFeeLabelsAndKeepsPublicSourceWarning() {
        var client = mock(EastmoneyFundFeeClient.class);
        when(client.fetchFeeHtml("005919")).thenReturn("""
                <div><h4>申购费率</h4><table><tbody>
                <tr><td>小于100万元</td><td>0.00%</td></tr></tbody></table></div>
                <div><h4>赎回费率</h4><table><tbody>
                <tr><td>小于7天</td><td>1.50%</td></tr></tbody></table></div>
                <table><tr><td>管理费率</td><td>0.50%（每年）</td></tr>
                <tr><td>托管费率</td><td>0.10%（每年）</td></tr>
                <tr><td>销售服务费率</td><td>0.20%（每年）</td></tr>
                <tr><td>申购状态</td><td>限制大额申购</td></tr>
                <tr><td>申购限额</td><td>单日 10 万元</td></tr>
                <tr><td>最低申购</td><td>10 元</td></tr></table>
                """);

        var result = new EastmoneyFundFeeSourceGateway(client).fetch("005919");

        assertThat(result.purchaseRate()).isEqualByComparingTo("0.00");
        assertThat(result.discountRate()).isEqualByComparingTo("0.00");
        assertThat(result.managementFee()).isEqualByComparingTo("0.005");
        assertThat(result.custodyFee()).isEqualByComparingTo("0.001");
        assertThat(result.salesServiceFee()).isEqualByComparingTo("0.002");
        assertThat(result.redemptionTiers()).singleElement()
                .satisfies(tier -> {
                    assertThat(tier.maxDays()).isEqualTo(7);
                    assertThat(tier.rate()).isEqualByComparingTo("0.015");
                });
        assertThat(result.purchaseStatus()).isEqualTo(PurchaseStatus.LIMITED);
        assertThat(result.purchaseLimit()).isEqualByComparingTo("100000");
        assertThat(result.minimumPurchaseAmount()).isEqualByComparingTo("10");
        assertThat(result.channelReferenceLabel())
                .isEqualTo("天天基金公开费率，仅供参考，以实际交易渠道为准");
        assertThat(result.sourceName()).isEqualTo("东方财富");
        assertThat(result.sourceUrl()).isEqualTo("https://fundf10.eastmoney.com/jjfl_005919.html");
    }

    @Test
    void allPublishedLabelsUnavailableReturnNullInsteadOfZeroes() {
        var client = mock(EastmoneyFundFeeClient.class);
        when(client.fetchFeeHtml("005919")).thenReturn("<section>费率页面结构已变化</section>");

        assertThat(new EastmoneyFundFeeSourceGateway(client).fetch("005919")).isNull();
    }

    @Test
    void missingPublishedFeeFieldsRemainNull() {
        var client = mock(EastmoneyFundFeeClient.class);
        when(client.fetchFeeHtml("005919"))
                .thenReturn("<table><tr><td>管理费率</td><td>0.50%（每年）</td></tr></table>");

        var result = new EastmoneyFundFeeSourceGateway(client).fetch("005919");

        assertThat(result.managementFee()).isEqualByComparingTo("0.005");
        assertThat(result.purchaseRate()).isNull();
        assertThat(result.discountRate()).isNull();
        assertThat(result.redemptionTiers()).isNull();
        assertThat(result.custodyFee()).isNull();
        assertThat(result.purchaseStatus()).isNull();
        assertThat(result.purchaseLimit()).isNull();
        assertThat(result.minimumPurchaseAmount()).isNull();
    }

    @Test
    void sourceTimeoutIsPropagatedToRefreshBoundary() {
        var client = mock(EastmoneyFundFeeClient.class);
        Request request = Request.create(Request.HttpMethod.GET, "https://example.test",
                Map.of(), (byte[]) null, StandardCharsets.UTF_8, new RequestTemplate());
        var timeout = new RetryableException(0, "timeout", Request.HttpMethod.GET,
                new SocketTimeoutException("timeout"), (Long) null, request);
        when(client.fetchFeeHtml("005919")).thenThrow(timeout);

        assertThatThrownBy(() -> new EastmoneyFundFeeSourceGateway(client).fetch("005919"))
                .isSameAs(timeout);
    }
}
