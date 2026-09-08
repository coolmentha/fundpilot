package com.fundpilot.backend.accounting.infrastructure.gateway.positiontracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import com.fundpilot.backend.productcatalog.adapter.api.fee.FundFeeApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpenLotValuationGatewayImplTest {

    @Test
    void 保留真实零费率且不把缺失费率伪装成零() {
        PublishedNavApi navs = mock(PublishedNavApi.class);
        FundProductApi products = mock(FundProductApi.class);
        FundFeeApi fees = mock(FundFeeApi.class);
        var gateway = new OpenLotValuationGatewayImpl(navs, products, fees);
        var product = new FundProductApi.Product(101L, "000001", "示例基金", null, null, null, null);
        when(products.findById(101L)).thenReturn(Optional.of(product));
        when(fees.findByFundCode("000001")).thenReturn(Optional.of(new FundFeeApi.FeeSchedule(
                null, null, null, List.of(new FundFeeApi.RedemptionTier(null, BigDecimal.ZERO)),
                Instant.parse("2026-09-07T00:00:00Z"))));

        assertThat(gateway.redemptionSchedule(101L)).hasValueSatisfying(schedule ->
                assertThat(schedule.rateFor(30)).contains(BigDecimal.ZERO));

        when(fees.findByFundCode("000001")).thenReturn(Optional.empty());
        assertThat(gateway.redemptionSchedule(101L)).isEmpty();
    }
}
