package com.fundpilot.backend.user;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.Actor;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi.ActorRole;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi.CreateUserRequest;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi.Role;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi.UserResult;
import com.fundpilot.backend.identityaccess.adapter.web.authentication.AuthenticationFilter;
import com.fundpilot.backend.identityaccess.application.gateway.authentication.SessionTokenGateway;
import com.fundpilot.backend.identityaccess.domain.user.UserRole;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@AutoConfigureMockMvc
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class MultiUserIsolationIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserAdministrationApi users;
    @Autowired FundRepository fundRepository;
    @Autowired FundNavHistoryRepository navRepository;
    @Autowired SessionTokenGateway sessions;
    @Autowired FundProductApi productCatalogApi;
    @Autowired PortfolioFundApi portfolioFundApi;

    @Test
    void usersSeeOnlyOwnedFundsAndShareNavByCode() throws Exception {
        UserResult admin = users.ensureBootstrapAdmin("isolation-admin", "test-password");
        Actor adminActor = new Actor(admin.id(), ActorRole.ADMIN, true);
        UserResult alice = user(adminActor, "isolation-alice");
        UserResult bob = user(adminActor, "isolation-bob");
        FundEntity aliceFund = fund(alice.id(), "000001", "Alice Fund");
        FundEntity bobFund = fund(bob.id(), "000001", "Bob Fund");
        FundNavHistoryEntity nav = new FundNavHistoryEntity();
        nav.setFundEntity(aliceFund);
        nav.setFundCode("000001");
        nav.setNavDate(Instant.parse("2026-07-21T00:00:00Z"));
        nav.setNav(new BigDecimal("1.23"));
        nav.setAccumulatedNav(new BigDecimal("2.34"));
        navRepository.save(nav);

        mockMvc.perform(get("/api/funds").cookie(cookie(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(aliceFund.getId()));
        mockMvc.perform(get("/api/funds/" + aliceFund.getId()).cookie(cookie(bob)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUND_NOT_FOUND"));

        assertThat(navRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(bobFund.getId()))
                .extracting(FundNavHistoryEntity::getId).containsExactly(nav.getId());
    }

    @Test
    void watchedIndicesAreIsolatedByAuthenticatedOwner() throws Exception {
        UserResult admin = users.ensureBootstrapAdmin("isolation-admin", "test-password");
        Actor adminActor = new Actor(admin.id(), ActorRole.ADMIN, true);
        UserResult alice = user(adminActor, "watched-indices-alice");
        UserResult bob = user(adminActor, "watched-indices-bob");

        mockMvc.perform(put("/api/market-data/watched-indices")
                        .cookie(cookie(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"indexCodes\":[\"1.000300\",\"1.000001\",\"1.000300\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexCodes[0]").value("1.000300"))
                .andExpect(jsonPath("$.data.indexCodes[1]").value("1.000001"))
                .andExpect(jsonPath("$.data.indexCodes.length()").value(2));

        mockMvc.perform(get("/api/market-data/watched-indices").cookie(cookie(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexCodes[0]").value("1.000001"))
                .andExpect(jsonPath("$.data.indexCodes[1]").value("1.000300"))
                .andExpect(jsonPath("$.data.indexCodes[2]").value("0.399006"));

        mockMvc.perform(get("/api/market-data/watched-indices").cookie(cookie(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexCodes[0]").value("1.000300"))
                .andExpect(jsonPath("$.data.indexCodes.length()").value(2));
    }

    private UserResult user(Actor admin, String username) {
        return users.create(admin, new CreateUserRequest(username, "test-password", Role.USER));
    }

    private FundEntity fund(Long ownerId, String code, String name) {
        FundEntity fund = new FundEntity();
        fund.setOwnerId(ownerId);
        fund.setFundCode(code);
        fund.setFundName(name);
        fund.setFundCategory(FundCategory.BROAD_BASE);
        var product = productCatalogApi.ensure(new FundProductApi.EnsureProduct(
                code, name, null, null));
        fund.setProductId(product.id());
        FundEntity saved = fundRepository.save(fund);
        portfolioFundApi.track(new PortfolioFundApi.TrackPortfolioFund(
                saved.getId(), ownerId, product.id(), saved.isPositionWarningEnabled(),
                saved.getPositionWarningRatio()));
        return saved;
    }

    private Cookie cookie(UserResult user) {
        return new Cookie(AuthenticationFilter.COOKIE_NAME, sessions.issue(user.id(), UserRole.USER));
    }
}
