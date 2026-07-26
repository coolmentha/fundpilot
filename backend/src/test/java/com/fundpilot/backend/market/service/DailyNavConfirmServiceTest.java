package com.fundpilot.backend.market.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.MarketDataSource;
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
class DailyNavConfirmServiceTest extends AbstractIntegrationTest {

    @MockitoBean
    MarketDataSource marketDataSource;

    @Autowired
    DailyNavConfirmService dailyNavConfirmService;

    @Autowired
    FundRepository fundRepository;

    @Autowired
    FundNavHistoryRepository fundNavHistoryRepository;

    @Autowired
    FundProductApi productCatalogApi;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE fund CASCADE");
    }

    @Test
    void 远端存在更新日期_增量落库累计净值() {
        String fundCode = uniqueCode();
        FundEntity fund = persistFund(fundCode);
        // 已落库净值最近一期 = 昨天(未确认今天)
        Instant today = ChinaTradingDate.toUtcDate(Instant.now());
        Instant yesterday = today.minus(1, java.time.temporal.ChronoUnit.DAYS);
        persistNav(fund, yesterday, "1.0000");
        when(marketDataSource.fetchNavHistory(fundCode)).thenReturn(List.of(
                new FundNavSnapshot(yesterday, new BigDecimal("1.0000"), new BigDecimal("1.0000")),
                new FundNavSnapshot(today, new BigDecimal("1.0100"), new BigDecimal("1.0100"))));

        dailyNavConfirmService.confirmTodayNav();

        // 今日累计净值已落库
        List<FundNavHistoryEntity> navs = fundNavHistoryRepository.findByFundEntity_Id(fund.getId());
        assertThat(navs).extracting(FundNavHistoryEntity::getAccumulatedNav)
                .anyMatch(value -> value.compareTo(new BigDecimal("1.0100")) == 0);
        assertThat(navs).filteredOn(nav -> nav.getNavDate().equals(today))
                .extracting(FundNavHistoryEntity::getFirstSeenAt).doesNotContainNull();
    }

    @Test
    void 已确认基金_指定日期已存在_跳过不重复拉取() {
        String fundCode = uniqueCode();
        FundEntity fund = persistFund(fundCode);
        // 已落库今日净值(已确认)
        Instant today = ChinaTradingDate.toUtcDate(Instant.now());
        persistNav(fund, today, "1.0200");
        dailyNavConfirmService.confirmTodayNav();

        verify(marketDataSource, never()).fetchNavHistory(fundCode);
        assertThat(fundNavHistoryRepository.findByFundEntity_Id(fund.getId())).hasSize(1);
    }

    @Test
    void QDII远端最新日期滞后于今天但晚于本地_仍按真实日期入库() {
        String fundCode = uniqueCode();
        FundEntity fund = persistFund(fundCode);
        Instant today = ChinaTradingDate.toUtcDate(Instant.now());
        Instant twoDaysAgo = today.minus(2, java.time.temporal.ChronoUnit.DAYS);
        Instant yesterday = today.minus(1, java.time.temporal.ChronoUnit.DAYS);
        persistNav(fund, twoDaysAgo, "1.0000");
        when(marketDataSource.fetchNavHistory(fundCode)).thenReturn(List.of(
                new FundNavSnapshot(yesterday, new BigDecimal("1.0100"), new BigDecimal("1.0100"))));

        dailyNavConfirmService.confirmTodayNav();

        assertThat(fundNavHistoryRepository.findByFundEntity_Id(fund.getId()))
                .extracting(FundNavHistoryEntity::getNavDate).contains(yesterday);
    }

    @Test
    void 缺失上一交易日净值_按指定日期补拉并落库() {
        String fundCode = uniqueCode();
        FundEntity fund = persistFund(fundCode);
        Instant today = ChinaTradingDate.toUtcDate(Instant.now());
        Instant previousTradingDay = today.minus(1, java.time.temporal.ChronoUnit.DAYS);
        when(marketDataSource.fetchNavHistory(fundCode)).thenReturn(List.of(
                new FundNavSnapshot(previousTradingDay, new BigDecimal("1.0100"),
                        new BigDecimal("1.0100"))));

        dailyNavConfirmService.confirmNavForDate(previousTradingDay);

        assertThat(fundNavHistoryRepository.findByFundEntity_Id(fund.getId()))
                .extracting(FundNavHistoryEntity::getNavDate)
                .contains(previousTradingDay);
    }

    private FundEntity persistFund(String code) {
        FundProductApi.ProductReference product = productCatalogApi.ensure(
                new FundProductApi.EnsureProduct(code, "测试基金", null, null));
        FundEntity fund = new FundEntity();
        fund.setProductId(product.id());
        fund.setFundCode(code);
        fund.setFundName("测试基金");
        return fundRepository.save(fund);
    }

    private void persistNav(FundEntity fund, Instant date, String nav) {
        FundNavHistoryEntity entity = new FundNavHistoryEntity();
        entity.setFundEntity(fund);
        entity.setNavDate(date);
        entity.setNav(new BigDecimal(nav));
        entity.setAccumulatedNav(new BigDecimal(nav));
        FundNavHistoryEntity saved = fundNavHistoryRepository.save(entity);
        jdbcTemplate.update("UPDATE fund_nav_history SET fund_product_id = ? WHERE id = ?",
                fund.getProductId(), saved.getId());
    }

    private String uniqueCode() {
        String value = Long.toString(System.nanoTime());
        return "T" + value.substring(Math.max(0, value.length() - 12));
    }
}
