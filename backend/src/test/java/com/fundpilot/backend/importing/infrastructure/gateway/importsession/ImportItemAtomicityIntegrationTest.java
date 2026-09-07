package com.fundpilot.backend.importing.infrastructure.gateway.importsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.fundpilot.backend.FundPilotBackendApplication;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.marketdata.adapter.api.indicatorrefresh.MarketIndicatorRefreshApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.NavPrefetchApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = FundPilotBackendApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/fundpilot?currentSchema=fundpilot_import_001",
        "spring.flyway.schemas=fundpilot_import_001",
        "spring.flyway.default-schema=fundpilot_import_001",
        "spring.jpa.properties.hibernate.default_schema=fundpilot_import_001"
})
class ImportItemAtomicityIntegrationTest {
    @Autowired ImportedHoldingGateway holdings;
    @Autowired PublishedNavApi navs;
    @Autowired UserAdministrationApi users;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MarketIndicatorRefreshApi refresh;
    @MockitoBean NavPrefetchApi prefetch;
    long ownerId;

    @BeforeEach
    void prepare() {
        ownerId = users.ensureBootstrapAdmin("import-001-admin", "integration-test-password").id();
        org.mockito.Mockito.when(prefetch.fetch(any())).thenReturn(
                List.of(new PublishedNavApi.NavCandidate(Instant.parse("2026-08-30T00:00:00Z"),
                        BigDecimal.ONE, BigDecimal.ONE)));
    }

    @Test
    void failedGroupWriteRollsBackProductPortfolioAndLedger() {
        String code = "imp-" + UUID.randomUUID();
        assertThatThrownBy(() -> holdings.create(ownerId, code, "导入测试", BigDecimal.TEN,
                BigDecimal.ONE, List.of(" "))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_product WHERE fund_code = ?",
                Long.class, code)).isZero();
    }
}
