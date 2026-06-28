package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.controller.FundView;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.service.MarketDataFetchService;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * issue #37 验收:建基金后自动拉历史净值。
 * <p>{@link FundService#create} save 基金后调 {@code MarketDataFetchService.fetchOneFund}。
 * 落库行为由 {@code MarketDataFetchServiceTest} 覆盖,本测试验证编排:调用了拉取 + 降级不阻断。
 * 用 @MockitoBean 替换 MarketDataFetchService,verify 调用而非真实落库(避免 REQUIRES_NEW 事务可见性问题)。
 */
class FundServiceAutoFetchTest extends AbstractIntegrationTest {

    @MockitoBean
    MarketDataFetchService marketDataFetchService;

    @Autowired
    FundService fundService;

    @Autowired
    FundRepository fundRepository;

    @Test
    @Transactional
    void create_建基金后调用fetchOneFund拉取净值() {
        FundView view = fundService.create(new FundCreateRequest(
                "161725", "测试基金", FundCategory.BROAD_BASE, FundSubType.ETF, "000300.SH", new BigDecimal("5000")));

        assertThat(view.id()).isNotNull();
        // save 后调用了 fetchOneFund 拉取历史净值
        verify(marketDataFetchService).fetchOneFund(view.id());
    }

    @Test
    @Transactional
    void create_净值拉取失败_基金仍创建成功() {
        // fetchOneFund 抛异常,模拟拉不到净值
        doThrow(new RuntimeException("东方财富不可达")).when(marketDataFetchService).fetchOneFund(anyLong());

        FundView view = fundService.create(new FundCreateRequest(
                "161726", "测试基金2", FundCategory.BROAD_BASE, FundSubType.ETF, "000300.SH", new BigDecimal("5000")));

        // 拉取失败降级:基金仍创建成功
        assertThat(view.id()).isNotNull();
        assertThat(fundRepository.existsById(view.id())).isTrue();
    }
}
