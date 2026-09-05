package com.fundpilot.backend.accounting.adapter.web.fundonboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.accounting.application.command.fundonboarding.PortfolioFundOnboardingCommandHandler;
import com.fundpilot.backend.accounting.application.command.fundonboarding.PortfolioFundOnboardingFailure;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PortfolioFundOnboardingController.class)
@Import({PortfolioFundOnboardingController.class, PortfolioFundOnboardingExceptionHandler.class,
        PortfolioFundOnboardingControllerTest.TestConfig.class})
class PortfolioFundOnboardingControllerTest {
    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PortfolioFundOnboardingCommandHandler onboarding;

    @Test
    void create_appliesDefaultsAndForwardsGroups() throws Exception {
        when(onboarding.onboard(isNull(), eq(7L), eq(9L), eq(true), eq(new BigDecimal("0.30")),
                eq(new BigDecimal("100")), eq(new BigDecimal("3.5")), isNull(), eq(List.of(" 核心 "))))
                .thenReturn(new PortfolioFundOnboardingCommandHandler.OnboardingResult(41L, 81L));

        mockMvc.perform(post("/api/portfolio-funds")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"fundProductId\":9,\"initialHoldingShares\":100,"
                                + "\"costPerShare\":3.5,\"groupNames\":[\" 核心 \"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.portfolioFundId").value(41))
                .andExpect(jsonPath("$.data.fundProductId").value(9))
                .andExpect(jsonPath("$.data.initialTransactionId").value(81));

        verify(onboarding).onboard(isNull(), eq(7L), eq(9L), eq(true), eq(new BigDecimal("0.30")),
                eq(new BigDecimal("100")), eq(new BigDecimal("3.5")), isNull(), eq(List.of(" 核心 ")));
    }

    @Test
    void create_mapsNavUnavailableTo422() throws Exception {
        var failure = org.mockito.Mockito.mock(PortfolioFundOnboardingFailure.class);
        when(failure.code()).thenReturn(PortfolioFundOnboardingFailure.Code.NAV_UNAVAILABLE);
        when(failure.getMessage()).thenReturn("净值不可用");
        when(onboarding.onboard(any(), anyLong(), anyLong(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenThrow(failure);

        mockMvc.perform(post("/api/portfolio-funds")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"fundProductId\":9,\"initialHoldingShares\":100}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NAV_UNAVAILABLE"));
    }
}
