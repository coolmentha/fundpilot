package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.EastmoneyClient;
import com.fundpilot.backend.market.client.RedemptionFeeSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 赎回费服务单测(issue #60):mock EastmoneyClient 返样例详情页,验证解析与费率查询、缺失降级。
 */
class RedemptionFeeServiceTest {

    @Test
    void 详情页含赎回费档位_返回解析结果() {
        EastmoneyClient client = Mockito.mock(EastmoneyClient.class);
        when(client.fetchRaw("510300.html")).thenReturn(
                "基金详情...赎回费率:持有少于7日1.50%,持有不少于7日少于30日0.75%,"
                        + "持有不少于30日少于365日0.50%,持有不少于365日0.00%...其他内容");
        RedemptionFeeService service = new RedemptionFeeService(client);

        RedemptionFeeSnapshot snapshot = service.fetch("510300");

        assertThat(snapshot.missing()).isFalse();
        assertThat(snapshot.tiers()).hasSize(4);
    }

    @Test
    void 持有10天_取第二档费率0_75百分比() {
        EastmoneyClient client = Mockito.mock(EastmoneyClient.class);
        when(client.fetchRaw("510300.html")).thenReturn(
                "赎回费率:持有少于7日1.50%,持有不少于7日少于30日0.75%,持有不少于365日0.00%");
        RedemptionFeeService service = new RedemptionFeeService(client);

        BigDecimal rate = service.feeRate("510300", 10);

        assertThat(rate).isEqualByComparingTo("0.0075");
    }

    @Test
    void 持有400天_取长持档0费率() {
        EastmoneyClient client = Mockito.mock(EastmoneyClient.class);
        when(client.fetchRaw("510300.html")).thenReturn(
                "赎回费率:持有少于7日1.50%,持有不少于365日0.00%");
        RedemptionFeeService service = new RedemptionFeeService(client);

        BigDecimal rate = service.feeRate("510300", 400);

        assertThat(rate).isEqualByComparingTo("0");
    }

    @Test
    void 拉取抛异常_降级返缺失快照不阻断() {
        EastmoneyClient client = Mockito.mock(EastmoneyClient.class);
        when(client.fetchRaw("510300.html")).thenThrow(new RuntimeException("网络异常"));
        RedemptionFeeService service = new RedemptionFeeService(client);

        RedemptionFeeSnapshot snapshot = service.fetch("510300");

        assertThat(snapshot.missing()).isTrue();
        assertThat(snapshot.tiers()).isEmpty();
    }

    @Test
    void 详情页无赎回费关键词_降级返缺失() {
        EastmoneyClient client = Mockito.mock(EastmoneyClient.class);
        when(client.fetchRaw("510300.html")).thenReturn("该基金暂无赎回费信息");
        RedemptionFeeService service = new RedemptionFeeService(client);

        RedemptionFeeSnapshot snapshot = service.fetch("510300");

        assertThat(snapshot.missing()).isTrue();
    }
}