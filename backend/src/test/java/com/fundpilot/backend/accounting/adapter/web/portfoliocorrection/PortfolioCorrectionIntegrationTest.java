package com.fundpilot.backend.accounting.adapter.web.portfoliocorrection;

import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundService;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@AutoConfigureMockMvc
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class PortfolioCorrectionIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired FundService fundService;
    @Autowired FundTransactionRepository transactions;
    @Autowired PortfolioFundApi portfolioFundApi;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void blocksPendingThenVoidsWithoutDeletingLedgerAndRetryKeepsFirstAudit() throws Exception {
        var fund = fundService.create(new FundCreateRequest(
                "009999", "录入错误基金", FundCategory.BROAD_BASE, FundSubType.INDEX, null));
        assertThat(jdbcTemplate.queryForMap("""
                SELECT category, source FROM discipline_classification WHERE portfolio_fund_id = ?
                """, fund.portfolioFundId()))
                .containsEntry("category", "BROAD_BASE")
                .containsEntry("source", "USER_CONFIRMED");
        FundEntity fundEntity = entityManager.find(FundEntity.class, fund.id());
        FundTransactionEntity pending = new FundTransactionEntity();
        pending.setFundEntity(fundEntity);
        pending.setStatus(FundTransactionStatus.PENDING);
        pending.setSource(FundTransactionSource.INCREASE);
        transactions.saveAndFlush(pending);

        mockMvc.perform(post("/api/portfolio-funds/{id}/void", fund.portfolioFundId())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"基金代码录入错误\",\"confirmed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_FUND_HAS_PENDING_TRANSACTIONS"));

        pending.setStatus(FundTransactionStatus.CANCELLED);
        transactions.saveAndFlush(pending);

        mockMvc.perform(post("/api/portfolio-funds/{id}/void", fund.portfolioFundId())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"基金代码录入错误\",\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(true))
                .andExpect(jsonPath("$.data.voidReason").value("基金代码录入错误"));

        assertThat(transactions.findById(pending.getId())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_date IS NOT NULL FROM fund WHERE id = ?",
                Boolean.class, fund.id())).isTrue();
        assertThat(fundService.list()).isEmpty();
        assertThat(portfolioFundApi.findOwned(testActorId(), fund.portfolioFundId()))
                .get().extracting(PortfolioFundApi.PortfolioFund::validity)
                .isEqualTo(PortfolioFundApi.Validity.VOIDED);

        mockMvc.perform(post("/api/portfolio-funds/{id}/void", fund.portfolioFundId())
                        .header(AuthenticationFilter.HEADER_NAME, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"第二次原因\",\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(false))
                .andExpect(jsonPath("$.data.voidReason").value("基金代码录入错误"));
    }
}
