package com.fundpilot.backend.portfolio.adapter.web.fundtracking;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundpilot.backend.platform.web.RequestActorAttributes;
import com.fundpilot.backend.portfolio.application.command.fundgrouping.FundGroupingCommandHandler;
import com.fundpilot.backend.portfolio.application.command.fundgrouping.FundGroupingFailure;
import com.fundpilot.backend.portfolio.application.command.fundtracking.PortfolioFundCommandHandler;
import com.fundpilot.backend.portfolio.application.command.fundtracking.PortfolioFundFailure;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PortfolioFundConfigurationController.class)
@Import({PortfolioFundConfigurationController.class, PortfolioFundConfigurationExceptionHandler.class,
        PortfolioFundConfigurationControllerTest.TestConfig.class})
class PortfolioFundConfigurationControllerTest {
    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PortfolioFundCommandHandler portfolioFunds;

    @MockitoBean
    FundGroupingCommandHandler groups;

    @Test
    void updatesWarningByPortfolioFundId() throws Exception {
        when(portfolioFunds.configureWarning(7L, 41L, true, new BigDecimal("0.25")))
                .thenReturn(new PortfolioFundCommandHandler.PortfolioFundResult(
                        41L, 7L, 9L, "TRACKED", true, new BigDecimal("0.25")));

        mockMvc.perform(put("/api/portfolio-funds/41/position-warning")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"ratio\":0.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.portfolioFundId").value(41))
                .andExpect(jsonPath("$.data.positionWarningEnabled").value(true))
                .andExpect(jsonPath("$.data.positionWarningRatio").value(0.25));

        verify(portfolioFunds).configureWarning(7L, 41L, true, new BigDecimal("0.25"));
    }

    @Test
    void mapsInvalidWarningToBadRequest() throws Exception {
        when(portfolioFunds.configureWarning(7L, 41L, true, BigDecimal.ZERO))
                .thenThrow(new PortfolioFundFailure(PortfolioFundFailure.Code.POSITION_WARNING_INVALID,
                        "仓位提醒比例必须大于 0 且不超过 1"));

        mockMvc.perform(put("/api/portfolio-funds/41/position-warning")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"ratio\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("POSITION_WARNING_INVALID"));
    }

    @Test
    void mapsRawOutOfRangeWarningToBadRequest() throws Exception {
        BigDecimal rawRatio = new BigDecimal("1.000000004");
        when(portfolioFunds.configureWarning(7L, 41L, true, rawRatio))
                .thenThrow(new PortfolioFundFailure(PortfolioFundFailure.Code.POSITION_WARNING_INVALID,
                        "仓位提醒比例必须大于 0 且不超过 1"));

        mockMvc.perform(put("/api/portfolio-funds/41/position-warning")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"ratio\":1.000000004}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("POSITION_WARNING_INVALID"));

        verify(portfolioFunds).configureWarning(7L, 41L, true, rawRatio);
    }

    @Test
    void replacesGroupsAndReturnsFinalMemberships() throws Exception {
        List<String> names = List.of(" 核心 ", "卫星");
        when(groups.assignByNames(7L, 41L, names)).thenReturn(List.of(
                new FundGroupingCommandHandler.GroupResult(3L, "核心", 0),
                new FundGroupingCommandHandler.GroupResult(4L, "卫星", 1)));

        mockMvc.perform(put("/api/portfolio-funds/41/groups")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"groupNames\":[\" 核心 \",\"卫星\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.portfolioFundId").value(41))
                .andExpect(jsonPath("$.data.groups[0].id").value(3))
                .andExpect(jsonPath("$.data.groups[0].name").value("核心"))
                .andExpect(jsonPath("$.data.groups[1].name").value("卫星"));

        verify(groups).assignByNames(7L, 41L, names);
    }

    @Test
    void mapsGroupValidationToBadRequest() throws Exception {
        List<String> names = List.of("核心", " 核心 ");
        when(groups.assignByNames(7L, 41L, names))
                .thenThrow(new FundGroupingFailure(FundGroupingFailure.Code.FUND_GROUP_NAME_DUPLICATE,
                        "分组名称不能重复"));

        mockMvc.perform(put("/api/portfolio-funds/41/groups")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"groupNames\":[\"核心\",\" 核心 \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FUND_GROUP_NAME_DUPLICATE"));
    }

    @Test
    void mapsControlCharacterInGroupNameToBadRequest() throws Exception {
        List<String> names = List.of("\u0007核心");
        when(groups.assignByNames(7L, 41L, names))
                .thenThrow(new FundGroupingFailure(FundGroupingFailure.Code.FUND_GROUP_NAME_INVALID,
                        "分组名称长度必须为 1-20 个字符且不能包含控制字符"));

        mockMvc.perform(put("/api/portfolio-funds/41/groups")
                        .requestAttr(RequestActorAttributes.USER_ID, 7L)
                        .contentType("application/json")
                        .content("{\"groupNames\":[\"\\u0007核心\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FUND_GROUP_NAME_INVALID"));

        verify(groups).assignByNames(7L, 41L, names);
    }
}
