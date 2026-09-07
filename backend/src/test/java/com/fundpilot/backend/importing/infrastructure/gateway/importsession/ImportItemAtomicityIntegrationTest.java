package com.fundpilot.backend.importing.infrastructure.gateway.importsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import com.fundpilot.backend.FundPilotBackendApplication;
import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.NavPrefetchApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    @Autowired UserAdministrationApi users;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MarketIndicatorRefreshCommandHandler refresh;
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
        String code = uniqueFundCode();
        String sessionId = UUID.randomUUID().toString();
        var request = new ImportedHoldingGateway.ItemRequest(ownerId, sessionId, "account:holding",
                code, "导入测试", BigDecimal.TEN, BigDecimal.ONE, List.of(" "), null);

        assertThatThrownBy(() -> holdings.importItem(request)).isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fund_product WHERE fund_code = ?",
                Long.class, code)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM portfolio_fund pf
                JOIN fund_product p ON p.id = pf.fund_product_id
                WHERE p.fund_code = ?
                """, Long.class, code)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM fund_transaction t
                JOIN portfolio_fund pf ON pf.id = t.portfolio_fund_id
                JOIN fund_product p ON p.id = pf.fund_product_id
                WHERE p.fund_code = ?
                """, Long.class, code)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM accounting_position pos
                JOIN portfolio_fund pf ON pf.id = pos.portfolio_fund_id
                JOIN fund_product p ON p.id = pf.fund_product_id
                WHERE p.fund_code = ?
                """, Long.class, code)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM import_item_receipt
                WHERE owner_id = ? AND session_id = ? AND item_id = ?
                """, Long.class, ownerId, sessionId, request.itemId())).isZero();

        var retried = holdings.importItem(new ImportedHoldingGateway.ItemRequest(ownerId, sessionId,
                request.itemId(), code, "导入测试", BigDecimal.TEN, BigDecimal.ONE,
                List.of("支付宝"), null));

        assertThat(retried.status()).isEqualTo(ImportedHoldingGateway.ItemStatus.CREATED);
        assertThat(transactionCount(code)).isOne();
        assertThat(count("SELECT count(*) FROM fund_product WHERE fund_code = ?", code)).isOne();
        assertThat(count("""
                SELECT count(*) FROM import_item_receipt
                WHERE owner_id = ? AND session_id = ? AND item_id = ?
                """, ownerId, sessionId, request.itemId())).isOne();
    }

    @Test
    void repeatedCompletedItemReusesCreatedResultAndBusinessFacts() {
        String code = uniqueFundCode();
        String sessionId = UUID.randomUUID().toString();
        var request = new ImportedHoldingGateway.ItemRequest(ownerId, sessionId, "account:holding",
                code, "导入测试", BigDecimal.TEN, new BigDecimal("1.25"), List.of("支付宝"), null);

        var first = holdings.importItem(request);
        var repeated = holdings.importItem(request);

        assertThat(first.status()).isEqualTo(ImportedHoldingGateway.ItemStatus.CREATED);
        assertThat(repeated).isEqualTo(first);
        assertThat(holdings.find(ownerId, code)).get().extracting(ImportedHoldingGateway.LocalHolding::shares)
                .isEqualTo(new BigDecimal("10.00"));
        assertThat(count("SELECT count(*) FROM fund_product WHERE fund_code = ?", code)).isOne();
        assertThat(count("""
                SELECT count(*) FROM portfolio_fund pf
                JOIN fund_product p ON p.id = pf.fund_product_id
                WHERE p.fund_code = ?
                """, code)).isOne();
        assertThat(count("""
                SELECT count(*) FROM fund_transaction t
                JOIN portfolio_fund pf ON pf.id = t.portfolio_fund_id
                JOIN fund_product p ON p.id = pf.fund_product_id
                WHERE p.fund_code = ?
                """, code)).isOne();
        assertThat(count("""
                SELECT count(*) FROM fund_lot lot
                JOIN portfolio_fund pf ON pf.id = lot.portfolio_fund_id
                JOIN fund_product p ON p.id = pf.fund_product_id
                WHERE p.fund_code = ?
                """, code)).isOne();
        assertThat(count("""
                SELECT count(*) FROM accounting_position pos
                JOIN portfolio_fund pf ON pf.id = pos.portfolio_fund_id
                JOIN fund_product p ON p.id = pf.fund_product_id
                WHERE p.fund_code = ?
                """, code)).isOne();
        assertThat(count("""
                SELECT count(*) FROM import_item_receipt
                WHERE owner_id = ? AND session_id = ? AND item_id = ?
                """, ownerId, sessionId, request.itemId())).isOne();
    }

    @Test
    void keepLocalPreservesFactsAndReturnsSkipped() {
        String code = uniqueFundCode();
        importNewHolding(code, BigDecimal.TEN);
        var request = new ImportedHoldingGateway.ItemRequest(ownerId, UUID.randomUUID().toString(),
                "account:keep", code, "导入测试", new BigDecimal("25"), BigDecimal.ONE,
                List.of("另一个账户"), ImportedHoldingGateway.ExistingMode.KEEP_LOCAL);

        var result = holdings.importItem(request);
        var repeated = holdings.importItem(request);

        assertThat(result.status()).isEqualTo(ImportedHoldingGateway.ItemStatus.SKIPPED);
        assertThat(repeated).isEqualTo(result);
        assertThat(holdings.find(ownerId, code)).get().extracting(ImportedHoldingGateway.LocalHolding::shares)
                .isEqualTo(new BigDecimal("10.00"));
        assertThat(transactionCount(code)).isOne();
    }

    @Test
    void syncTargetAdjustsOnceAndRepeatedTargetsDoNotAddLedgerEntries() {
        String code = uniqueFundCode();
        importNewHolding(code, BigDecimal.TEN);
        var request = new ImportedHoldingGateway.ItemRequest(ownerId, UUID.randomUUID().toString(),
                "account:sync", code, "导入测试", new BigDecimal("15"), BigDecimal.ONE,
                List.of("另一个账户"), ImportedHoldingGateway.ExistingMode.SYNC_TARGET);

        var result = holdings.importItem(request);
        var repeated = holdings.importItem(request);
        var sameTarget = holdings.importItem(new ImportedHoldingGateway.ItemRequest(ownerId,
                UUID.randomUUID().toString(), "account:sync-again", code, "导入测试",
                new BigDecimal("15.00"), BigDecimal.ONE, List.of("另一个账户"),
                ImportedHoldingGateway.ExistingMode.SYNC_TARGET));

        assertThat(result.status()).isEqualTo(ImportedHoldingGateway.ItemStatus.ADJUSTED);
        assertThat(repeated).isEqualTo(result);
        assertThat(sameTarget.status()).isEqualTo(ImportedHoldingGateway.ItemStatus.ADJUSTED);
        assertThat(holdings.find(ownerId, code)).get().extracting(ImportedHoldingGateway.LocalHolding::shares)
                .isEqualTo(new BigDecimal("15.00"));
        assertThat(transactionCount(code)).isEqualTo(2);
    }

    @Test
    void concurrentRepeatedItemCreatesOneSetOfBusinessFacts() throws Exception {
        String code = uniqueFundCode();
        var request = new ImportedHoldingGateway.ItemRequest(ownerId, UUID.randomUUID().toString(),
                "account:concurrent", code, "导入测试", BigDecimal.TEN, BigDecimal.ONE,
                List.of("支付宝"), null);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return holdings.importItem(request);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return holdings.importItem(request);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(transactionCount(code)).isOne();
        assertThat(count("SELECT count(*) FROM fund_product WHERE fund_code = ?", code)).isOne();
        assertThat(count("""
                SELECT count(*) FROM portfolio_fund pf
                JOIN fund_product p ON p.id = pf.fund_product_id
                WHERE p.fund_code = ?
                """, code)).isOne();
        assertThat(count("""
                SELECT count(*) FROM import_item_receipt
                WHERE owner_id = ? AND session_id = ? AND item_id = ?
                """, ownerId, request.sessionId(), request.itemId())).isOne();
    }

    private ImportedHoldingGateway.ItemResult importNewHolding(String code, BigDecimal shares) {
        return holdings.importItem(new ImportedHoldingGateway.ItemRequest(ownerId,
                UUID.randomUUID().toString(), "account:initial", code, "导入测试", shares,
                BigDecimal.ONE, List.of("支付宝"), null));
    }

    private long transactionCount(String code) {
        return count("""
                SELECT count(*) FROM fund_transaction t
                JOIN portfolio_fund pf ON pf.id = t.portfolio_fund_id
                JOIN fund_product p ON p.id = pf.fund_product_id
                WHERE p.fund_code = ?
                """, code);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static String uniqueFundCode() {
        return "I" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
    }
}
