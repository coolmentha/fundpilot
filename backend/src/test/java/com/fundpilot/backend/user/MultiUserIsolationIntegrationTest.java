package com.fundpilot.backend.user;

import com.fundpilot.backend.admin.security.AdminSessionTokenService;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import com.fundpilot.backend.user.entity.SiteUserEntity;
import com.fundpilot.backend.user.entity.UserRole;
import com.fundpilot.backend.user.repository.SiteUserRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = "fundpilot.admin.api-key=test-admin-key")
class MultiUserIsolationIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired SiteUserRepository userRepository;
    @Autowired FundRepository fundRepository;
    @Autowired FundNavHistoryRepository navRepository;
    @Autowired AdminSessionTokenService sessions;

    @Test
    void usersSeeOnlyOwnedFundsAndShareNavByCode() throws Exception {
        SiteUserEntity alice = user("isolation-alice");
        SiteUserEntity bob = user("isolation-bob");
        FundEntity aliceFund = fund(alice.getId(), "000001", "Alice Fund");
        FundEntity bobFund = fund(bob.getId(), "000001", "Bob Fund");
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

    private SiteUserEntity user(String username) {
        SiteUserEntity user = new SiteUserEntity();
        user.setUsername(username);
        user.setPasswordHash("test-only");
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private FundEntity fund(Long ownerId, String code, String name) {
        FundEntity fund = new FundEntity();
        fund.setOwnerId(ownerId);
        fund.setFundCode(code);
        fund.setFundName(name);
        fund.setFundCategory(FundCategory.BROAD_BASE);
        return fundRepository.save(fund);
    }

    private Cookie cookie(SiteUserEntity user) {
        return new Cookie(AdminSessionTokenService.COOKIE_NAME, sessions.issue(user.getId(), user.getRole()));
    }
}
