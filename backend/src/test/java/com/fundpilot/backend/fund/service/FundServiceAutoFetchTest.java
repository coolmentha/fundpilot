package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.controller.FundView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.*;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.market.service.MarketDataFetchService;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import com.fundpilot.backend.user.entity.UserConfigEntity;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * issue #37 验收:建基金后自动拉历史净值。
 * <p>{@link FundService#create} save 基金后调 {@code MarketDataFetchService.fetchOneFund}。
 * 落库行为由 {@code MarketDataFetchServiceTest} 覆盖,本测试验证编排:调用了拉取 + 降级不阻断。
 * 用 @MockitoBean 替换 MarketDataFetchService,verify 调用而非真实落库(避免 REQUIRES_NEW 事务可见性问题)。
 *
 * <p>ADR-0012 初始持仓录入:initialMarketValue 有值时同步确认建仓交易 + 状态流转,用 doAnswer 模拟
 * fetchOneFund 落净值历史(真实拉取走网络,mock 替换)。
 */
class FundServiceAutoFetchTest extends AbstractIntegrationTest {

    @MockitoBean
    MarketDataFetchService marketDataFetchService;

    @Autowired
    FundService fundService;

    @Autowired
    FundRepository fundRepository;

    @Autowired
    FundNavHistoryRepository fundNavHistoryRepository;

    @Autowired
    FundTransactionRepository fundTransactionRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    UserConfigRepository userConfigRepository;

    @BeforeEach
    void setUpUserConfig() {
        // FundService.create → validatePlannedTotalAmount → requireTotalInvestableCapital 依赖 user_config 已初始化;
        // CI 全新库无此行会抛 USER_CONFIG_NOT_INITIALIZED。每个测试前清空+插入唯一可控资金值(对齐 FundServiceTest)。
        // @Transactional 测试方法下随事务回滚;非事务测试方法下 repository.save 自动提交(后者需 Service 独立事务读到)。
        userConfigRepository.deleteAll();
        UserConfigEntity config = new UserConfigEntity();
        config.setTotalInvestableCapital(new BigDecimal("100000"));
        userConfigRepository.save(config);
    }

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

    @Test
    @Transactional
    void create_录现有金额_用最近净值同步确认建仓交易_状态流转HOLDING() {
        // fetchOneFund 落一期净值(模拟拉取成功):最近净值 1.5
        doAnswer(inv -> {
            Long fundId = inv.getArgument(0);
            persistNav(fundId, Instant.now(), new BigDecimal("1.5"));
            return null;
        }).when(marketDataFetchService).fetchOneFund(anyLong());

        FundView view = fundService.create(new FundCreateRequest(
                "161727", "现有持仓基金", FundCategory.BROAD_BASE, FundSubType.ETF, "000300.SH",
                new BigDecimal("5000"), new BigDecimal("3000")));

        // 状态流转:HOLDING + openedAt 已设
        entityManager.flush();
        entityManager.clear();
        FundEntity fund = fundRepository.findById(view.id()).orElseThrow();
        assertThat(fund.getStatus()).isEqualTo(FundStatus.HOLDING);
        assertThat(fund.getOpenedAt()).isNotNull();

        // 建仓交易:INCREASE + CONFIRMED + shares=3000/1.5=2000 + nav=1.5
        List<FundTransactionEntity> txs = fundTransactionRepository.findByFundEntity_IdOrderByCreatedDateDesc(view.id());
        assertThat(txs).hasSize(1);
        FundTransactionEntity tx = txs.get(0);
        assertThat(tx.getSource()).isEqualTo(FundTransactionSource.INCREASE);
        assertThat(tx.getStatus()).isEqualTo(FundTransactionStatus.CONFIRMED);
        assertThat(tx.getAmount()).isEqualByComparingTo("3000");
        assertThat(tx.getNav()).isEqualByComparingTo("1.5");
        assertThat(tx.getShares()).isEqualByComparingTo("2000");
        // 6079ba1:confirmTime 语义改为 openedAt;本场景未传 openedAt → confirmTime=null(同步确认不伪造时点)
        assertThat(tx.getConfirmTime()).isNull();
        assertThat(tx.getSignalLogEntity()).isNull(); // 绕过信号
    }

    @Test
    @Transactional
    void create_录现有金额填建仓时间_openedAt用用户填值() {
        doAnswer(inv -> {
            Long fundId = inv.getArgument(0);
            persistNav(fundId, Instant.now(), new BigDecimal("1.5"));
            return null;
        }).when(marketDataFetchService).fetchOneFund(anyLong());

        Instant userOpenedAt = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        FundView view = fundService.create(new FundCreateRequest(
                "161730", "带建仓时间基金", FundCategory.BROAD_BASE, FundSubType.ETF, "000300.SH",
                new BigDecimal("5000"), new BigDecimal("3000"), userOpenedAt));

        entityManager.flush();
        entityManager.clear();
        FundEntity fund = fundRepository.findById(view.id()).orElseThrow();
        // openedAt 用用户填值(历史日期),不是 now(DB timestamp 精度可能截断,按秒比较)
        assertThat(fund.getOpenedAt().getEpochSecond()).isEqualTo(userOpenedAt.getEpochSecond());
        // 6079ba1:confirmTime 语义改为 openedAt,与建仓时间一致(不再用 now)
        FundTransactionEntity tx = fundTransactionRepository.findByFundEntity_IdOrderByCreatedDateDesc(view.id()).get(0);
        assertThat(tx.getConfirmTime().getEpochSecond()).isEqualTo(userOpenedAt.getEpochSecond());
    }

    @Test
    void create_录现有金额填未来建仓时间_抛OPENED_AT_IN_FUTURE() {
        // 不加 @Transactional:Service 事务独立运行,抛异常真实回滚
        doAnswer(inv -> {
            Long fundId = inv.getArgument(0);
            persistNav(fundId, Instant.now(), new BigDecimal("1.5"));
            return null;
        }).when(marketDataFetchService).fetchOneFund(anyLong());

        Instant futureOpenedAt = Instant.now().plus(10, java.time.temporal.ChronoUnit.DAYS);
        long before = fundRepository.count();

        assertThatThrownBy(() -> fundService.create(new FundCreateRequest(
                "161731", "未来建仓基金", FundCategory.BROAD_BASE, FundSubType.ETF, "000300.SH",
                new BigDecimal("5000"), new BigDecimal("3000"), futureOpenedAt)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.OPENED_AT_IN_FUTURE.name());

        // 基金未落库
        assertThat(fundRepository.count()).isEqualTo(before);
    }

    @Test
    void create_录现有金额但无净值历史_抛NAV_HISTORY_EMPTY且基金不落库() {
        // fetchOneFund 不落净值(模拟拉取失败但未抛异常,或新基金无历史)
        // 不加 @Transactional:Service 事务独立运行,抛异常真实回滚,count 才能验证未落库
        long before = fundRepository.count();

        assertThatThrownBy(() -> fundService.create(new FundCreateRequest(
                "161728", "无净值基金", FundCategory.BROAD_BASE, FundSubType.ETF, "000300.SH",
                new BigDecimal("5000"), new BigDecimal("3000"))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.NAV_HISTORY_EMPTY.name());

        // 基金未落库(Service 事务回滚)
        assertThat(fundRepository.count()).isEqualTo(before);
    }

    @Test
    @Transactional
    void create_不录现有金额_走原PENDING_HOLDING流程不建仓() {
        FundView view = fundService.create(new FundCreateRequest(
                "161729", "空仓基金", FundCategory.BROAD_BASE, FundSubType.ETF, "000300.SH",
                new BigDecimal("5000"), null));

        entityManager.flush();
        entityManager.clear();
        FundEntity fund = fundRepository.findById(view.id()).orElseThrow();
        // 原流程:未建仓
        assertThat(fund.getStatus()).isEqualTo(FundStatus.PENDING_HOLDING);
        assertThat(fund.getOpenedAt()).isNull();
        // 无交易
        assertThat(fundTransactionRepository.findByFundEntity_IdOrderByCreatedDateDesc(view.id())).isEmpty();
    }

    private void persistNav(Long fundId, Instant date, BigDecimal accumulatedNav) {
        FundEntity fund = entityManager.find(FundEntity.class, fundId);
        FundNavHistoryEntity nav = new FundNavHistoryEntity();
        nav.setFundEntity(fund);
        nav.setNavDate(date);
        nav.setNav(accumulatedNav);
        nav.setAccumulatedNav(accumulatedNav);
        entityManager.persist(nav);
    }
}
