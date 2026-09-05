package com.fundpilot.backend.portfolio.adapter.web.fundgrouping;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.portfolio.adapter.api.fundgrouping.PortfolioGroupingApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@AutoConfigureMockMvc
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class FundGroupingIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired FundRepository fundRepository;
    @Autowired FundProductApi productCatalogApi;
    @Autowired PortfolioFundApi portfolioFundApi;
    @Autowired PortfolioGroupingApi portfolioGroupingApi;
    @Autowired SessionTokenGateway sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void webEntryOwnsGroupsAndKeepsBothMembershipProjectionsConsistent() throws Exception {
        long ownerId = testActorId();
        var product = productCatalogApi.ensure(new FundProductApi.EnsureProduct(
                "510300", "沪深300ETF", null, null));
        FundEntity fund = new FundEntity();
        fund.setOwnerId(ownerId);
        fund.setProductId(product.id());
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund = fundRepository.saveAndFlush(fund);
        var portfolioFund = portfolioFundApi.track(new PortfolioFundApi.TrackPortfolioFund(
                fund.getId(), ownerId, product.id(), fund.isPositionWarningEnabled(),
                fund.getPositionWarningRatio()));
        Cookie actor = new Cookie(AuthenticationFilter.COOKIE_NAME,
                sessions.issue(ownerId, UserRole.ADMIN, 0L));

        mockMvc.perform(put("/api/fund-groups").cookie(actor)
                        .contentType("application/json")
                        .content("{\"groups\":[{\"id\":null,\"name\":\"核心\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("核心"));
        mockMvc.perform(get("/api/fund-groups").cookie(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fundCount").value(0));

        portfolioGroupingApi.assignByNames(new PortfolioGroupingApi.AssignByNames(
                ownerId, portfolioFund.id(), java.util.List.of("核心")));

        mockMvc.perform(get("/api/fund-groups").cookie(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fundCount").value(1));
        assertThat(countMembership("portfolio_fund_group_member", "portfolio_fund_id",
                portfolioFund.id())).isEqualTo(1);
        assertThat(countMembership("fund_group_member", "fund_id", fund.getId())).isEqualTo(1);

        mockMvc.perform(put("/api/fund-groups").cookie(actor)
                        .contentType("application/json").content("{\"groups\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        assertThat(fundRepository.findById(fund.getId())).isPresent();
        assertThat(countMembership("portfolio_fund_group_member", "portfolio_fund_id",
                portfolioFund.id())).isZero();
        assertThat(countMembership("fund_group_member", "fund_id", fund.getId())).isZero();
    }

    @Test
    void concurrentAssignmentsReuseOneOwnerScopedGroup() throws Exception {
        long ownerId = testActorId();
        var first = createTrackedFund(ownerId, "concurrent-first");
        var second = createTrackedFund(ownerId, "concurrent-second");
        String groupName = "并发-" + Long.toUnsignedString(System.nanoTime(), 36);
        CyclicBarrier barrier = new CyclicBarrier(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> firstAssignment = null;
        Future<?> secondAssignment = null;
        try {
            firstAssignment = executor.submit(() -> {
                assignInTransaction(ownerId, first.id(), groupName, barrier, transaction);
                return null;
            });
            secondAssignment = executor.submit(() -> {
                assignInTransaction(ownerId, second.id(), groupName, barrier, transaction);
                return null;
            });
            firstAssignment.get(10, TimeUnit.SECONDS);
            secondAssignment.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM fund_group
                WHERE owner_id = ? AND lower(name) = lower(?) AND deleted_date IS NULL
                """, Long.class, ownerId, groupName)).isEqualTo(1L);
        List<Long> groupIds = jdbc.queryForList("""
                SELECT group_id FROM portfolio_fund_group_member
                WHERE portfolio_fund_id IN (?, ?) ORDER BY portfolio_fund_id
                """, Long.class, first.id(), second.id());
        assertThat(groupIds).hasSize(2).containsOnly(groupIds.getFirst());
    }

    private void assignInTransaction(long ownerId, long portfolioFundId, String groupName,
                                     CyclicBarrier barrier, TransactionTemplate transaction)
            throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        transaction.execute(status -> {
            portfolioGroupingApi.assignByNames(new PortfolioGroupingApi.AssignByNames(
                    ownerId, portfolioFundId, List.of(groupName)));
            return null;
        });
    }

    private PortfolioFundApi.PortfolioFund createTrackedFund(long ownerId, String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        var product = productCatalogApi.ensure(new FundProductApi.EnsureProduct(
                "G" + suffix, prefix + " ETF", null, FundProductApi.InvestmentTarget.STOCK));
        FundEntity fund = new FundEntity();
        fund.setOwnerId(ownerId);
        fund.setProductId(product.id());
        fund.setFundCode(product.fundCode());
        fund.setFundName(prefix + " ETF");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund = fundRepository.saveAndFlush(fund);
        return portfolioFundApi.track(new PortfolioFundApi.TrackPortfolioFund(
                fund.getId(), ownerId, product.id(), fund.isPositionWarningEnabled(),
                fund.getPositionWarningRatio()));
    }

    private int countMembership(String table, String ownerColumn, long ownerId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE "
                + ownerColumn + " = ?", Integer.class, ownerId);
    }
}
