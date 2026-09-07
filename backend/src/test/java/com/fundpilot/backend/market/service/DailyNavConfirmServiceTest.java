package com.fundpilot.backend.marketdata.application.command.navpublishing;

import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavSourceGateway;
import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * issue #39 验收:当晚净值确认拉取。
 * <p>20-23 点每 5 分钟直接拉净值历史，按远端日期晚于本地最新日期增量落库。
 */
class DailyNavPublishingCommandHandlerTest extends AbstractIntegrationTest {

    @MockitoBean
    PublishedNavSourceGateway navSource;

    @MockitoBean
    MarketIndicatorRefreshCommandHandler indicatorRefresh;

    @Autowired
    DailyNavPublishingCommandHandler navPublishing;

    @Autowired
    PublishedNavRepository publishedNavRepository;

    @Autowired
    FundProductApi productCatalogApi;

    @Autowired
    PortfolioFundApi portfolioFundApi;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE fund_product CASCADE");
    }

    @Test
    void 远端存在更新日期_增量落库累计净值() {
        String fundCode = uniqueCode();
        FundProductApi.ProductReference product = trackProduct(fundCode);
        // 已落库净值最近一期 = 昨天(未确认今天)
        Instant today = ChinaTradingDate.toUtcDate(Instant.now());
        Instant yesterday = today.minus(1, java.time.temporal.ChronoUnit.DAYS);
        persistNav(product, yesterday, "1.0000");
        when(navSource.fetchHistory(fundCode)).thenReturn(List.of(
                new PublishedNavSourceGateway.NavSnapshot(yesterday, new BigDecimal("1.0000"), new BigDecimal("1.0000")),
                new PublishedNavSourceGateway.NavSnapshot(today, new BigDecimal("1.0100"), new BigDecimal("1.0100"))));

        navPublishing.publishToday();

        // 今日累计净值已落库
        assertThat(publishedNavRepository.findLatestByProductId(product.id())).get().satisfies(nav -> {
            assertThat(nav.accumulatedNav()).isEqualByComparingTo("1.0100");
            assertThat(nav.navDate()).isEqualTo(today);
            assertThat(nav.firstSeenAt()).isNotNull();
        });
    }

    @Test
    void 已确认基金_指定日期已存在_跳过不重复拉取() {
        String fundCode = uniqueCode();
        FundProductApi.ProductReference product = trackProduct(fundCode);
        // 已落库今日净值(已确认)
        Instant today = ChinaTradingDate.toUtcDate(Instant.now());
        persistNav(product, today, "1.0200");
        navPublishing.publishToday();

        verify(navSource, never()).fetchHistory(fundCode);
        assertThat(publishedNavRepository.findLatestByProductId(product.id())).get()
                .extracting(PublishedNav::unitNav)
                .satisfies(unitNav -> assertThat(unitNav).isEqualByComparingTo("1.0200"));
    }

    @Test
    void QDII远端最新日期滞后于今天但晚于本地_仍按真实日期入库() {
        String fundCode = uniqueCode();
        FundProductApi.ProductReference product = trackProduct(fundCode);
        Instant today = ChinaTradingDate.toUtcDate(Instant.now());
        Instant twoDaysAgo = today.minus(2, java.time.temporal.ChronoUnit.DAYS);
        Instant yesterday = today.minus(1, java.time.temporal.ChronoUnit.DAYS);
        persistNav(product, twoDaysAgo, "1.0000");
        when(navSource.fetchHistory(fundCode)).thenReturn(List.of(
                new PublishedNavSourceGateway.NavSnapshot(yesterday, new BigDecimal("1.0100"), new BigDecimal("1.0100"))));

        navPublishing.publishToday();

        assertThat(publishedNavRepository.findLatestByProductId(product.id())).get()
                .extracting(PublishedNav::navDate).isEqualTo(yesterday);
    }

    @Test
    void 缺失上一交易日净值_按指定日期补拉并落库() {
        String fundCode = uniqueCode();
        FundProductApi.ProductReference product = trackProduct(fundCode);
        Instant today = ChinaTradingDate.toUtcDate(Instant.now());
        Instant previousTradingDay = today.minus(1, java.time.temporal.ChronoUnit.DAYS);
        when(navSource.fetchHistory(fundCode)).thenReturn(List.of(
                new PublishedNavSourceGateway.NavSnapshot(previousTradingDay, new BigDecimal("1.0100"),
                        new BigDecimal("1.0100"))));

        navPublishing.publishForDate(previousTradingDay);

        assertThat(publishedNavRepository.findLatestByProductId(product.id())).get()
                .extracting(PublishedNav::navDate).isEqualTo(previousTradingDay);
    }

    private FundProductApi.ProductReference trackProduct(String code) {
        FundProductApi.ProductReference product = productCatalogApi.ensure(
                new FundProductApi.EnsureProduct(code, "测试基金", null, null));
        portfolioFundApi.track(new PortfolioFundApi.TrackPortfolioFund(null, testActorId(),
                product.id(), true, new BigDecimal("0.30")));
        return product;
    }

    private void persistNav(FundProductApi.ProductReference product, Instant date, String nav) {
        BigDecimal value = new BigDecimal(nav);
        publishedNavRepository.saveAll(List.of(PublishedNav.publish(
                null, product.id(), product.fundCode(), date, value, value, date)));
    }

    private String uniqueCode() {
        String value = Long.toString(System.nanoTime());
        return "T" + value.substring(Math.max(0, value.length() - 12));
    }
}
