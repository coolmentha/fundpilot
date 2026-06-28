package com.fundpilot.backend.market.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.MarketDataSource;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * issue #39 验收:当晚净值确认拉取。
 * <p>20-23 点每分钟,查未确认基金(最近 navDate≠今天)→ fundgz 判 jzrq=今天 → pingzhongdata 落累计净值;
 * 已确认跳过。
 */
class DailyNavConfirmServiceTest extends AbstractIntegrationTest {

    @MockitoBean
    MarketDataSource marketDataSource;

    @MockitoBean
    FundEstimateService fundEstimateService;

    @Autowired
    DailyNavConfirmService dailyNavConfirmService;

    @Autowired
    FundRepository fundRepository;

    @Autowired
    FundNavHistoryRepository fundNavHistoryRepository;

    @Test
    @Transactional
    void 未确认基金_fundgz判定jzrq今天_拉pingzhongdata落库当日累计净值() {
        FundEntity fund = persistFund("161725");
        // 已落库净值最近一期 = 昨天(未确认今天)
        Instant yesterday = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1)
                .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        persistNav(fund, yesterday, "1.0000");
        // fundgz 判定:jzrq = 今天(已公布)
        Instant today = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        when(fundEstimateService.fetchEstimate("161725")).thenReturn(Optional.of(
                new FundEstimateSnapshot(new BigDecimal("0.01"), "today 15:00", today.toString())));
        // pingzhongdata 返回含今日的累计净值序列
        when(marketDataSource.fetchNavHistory("161725")).thenReturn(List.of(
                new FundNavSnapshot(yesterday, new BigDecimal("1.0000"), new BigDecimal("1.0000")),
                new FundNavSnapshot(today, new BigDecimal("1.0100"), new BigDecimal("1.0100"))));

        dailyNavConfirmService.confirmTodayNav();

        // 今日累计净值已落库
        List<FundNavHistoryEntity> navs = fundNavHistoryRepository.findByFundEntity_Id(fund.getId());
        assertThat(navs).extracting(FundNavHistoryEntity::getAccumulatedNav)
                .contains(new BigDecimal("1.0100"));
    }

    @Test
    @Transactional
    void 已确认基金_最近navDate今天_跳过不重复拉取() {
        FundEntity fund = persistFund("161726");
        // 已落库今日净值(已确认)
        Instant today = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        persistNav(fund, today, "1.0200");

        dailyNavConfirmService.confirmTodayNav();

        // 已确认,不调 fundgz / pingzhongdata
        verify(fundEstimateService, never()).fetchEstimate(anyString());
        verify(marketDataSource, never()).fetchNavHistory(anyString());
    }

    @Test
    @Transactional
    void 未确认基金_fundgz判定jzrq非今天_跳过_净值未公布() {
        FundEntity fund = persistFund("161727");
        Instant yesterday = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1)
                .toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        persistNav(fund, yesterday, "1.0000");
        // fundgz 判定:jzrq = 昨天(今日净值还没公布)
        when(fundEstimateService.fetchEstimate("161727")).thenReturn(Optional.of(
                new FundEstimateSnapshot(new BigDecimal("0.01"), "today 15:00", yesterday.toString())));

        dailyNavConfirmService.confirmTodayNav();

        // 净值未公布,不拉 pingzhongdata
        verify(marketDataSource, never()).fetchNavHistory(anyString());
    }

    private FundEntity persistFund(String code) {
        FundEntity fund = new FundEntity();
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
        fundNavHistoryRepository.save(entity);
    }
}
