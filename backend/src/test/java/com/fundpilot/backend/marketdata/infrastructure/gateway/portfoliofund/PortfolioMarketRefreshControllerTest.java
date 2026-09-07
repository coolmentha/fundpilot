package com.fundpilot.backend.marketdata.infrastructure.gateway.portfoliofund;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.marketdata.adapter.web.indicatorrefresh.PortfolioMarketRefreshController;
import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.*;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PortfolioMarketRefreshController.class)
@Import({PortfolioMarketRefreshController.class, PortfolioMarketRefreshCommandHandler.class,
        OwnedFundProductGatewayImpl.class, PortfolioMarketRefreshControllerTest.TestConfig.class})
class PortfolioMarketRefreshControllerTest {
    @SpringBootConfiguration
    @ComponentScan(basePackageClasses = BusinessException.class)
    static class TestConfig {}

    @Autowired MockMvc mvc;
    @MockitoBean CurrentActorApi actor;
    @MockitoBean PortfolioFundApi portfolios;
    @MockitoBean FundProductApi products;
    @MockitoBean MarketIndicatorRefreshCommandHandler refresh;
    @MockitoBean MeterRegistry metrics;

    @Test
    void trackedOwnerUsesPortfolioIdDirectly() throws Exception {
        owned(PortfolioFundApi.Validity.TRACKED);
        mvc.perform(post("/api/portfolio-funds/41/market-data/refresh"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.portfolioFundId").value(41));
        verify(portfolios).findOwned(3L, 41L);
        verify(portfolios, never()).findOwnedByLegacyFundId(anyLong(), anyLong());
        verify(refresh).refreshOneForPortfolioFund(41L);
    }

    @Test
    void differentOwnerOrMissingTargetCannotRefresh() throws Exception {
        when(actor.userId()).thenReturn(4L);
        mvc.perform(post("/api/portfolio-funds/41/market-data/refresh"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FUND_NOT_FOUND"));
        verify(portfolios).findOwned(4L, 41L);
        verifyNoInteractions(products, refresh);
    }

    @Test
    void voidedTargetCannotRefresh() throws Exception {
        owned(PortfolioFundApi.Validity.VOIDED);
        mvc.perform(post("/api/portfolio-funds/41/market-data/refresh"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FUND_NOT_FOUND"));
        verifyNoInteractions(refresh);
    }

    @Test
    void externalFailureDoesNotExposeInternalDetailsOrClaimSuccess() throws Exception {
        owned(PortfolioFundApi.Validity.TRACKED);
        doThrow(new IllegalStateException("private upstream detail")).when(refresh).refreshOneForPortfolioFund(41L);
        mvc.perform(post("/api/portfolio-funds/41/market-data/refresh"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MARKET_DATA_ALL_SOURCES_FAILED"))
                .andExpect(jsonPath("$.message").value("行情刷新失败，请稍后重试"));
    }

    private void owned(PortfolioFundApi.Validity validity) {
        when(actor.userId()).thenReturn(3L);
        when(portfolios.findOwned(3L, 41L)).thenReturn(Optional.of(new PortfolioFundApi.PortfolioFund(
                41L, null, 3L, 7L, validity, true, new BigDecimal("0.30"), null, null, null)));
        var product = mock(FundProductApi.Product.class);
        when(product.id()).thenReturn(7L);
        when(product.fundCode()).thenReturn("000001");
        when(products.findById(7L)).thenReturn(Optional.of(product));
    }
}
