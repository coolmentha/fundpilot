package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.MarketDataSource;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.service.support.BenchmarkMetrics;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * issue #28 验收:{@link StrategyOptimizeService} 样本外验证与落库编排。
 * <p>连真实 PG,复用 {@link DefaultStrategyBacktestServiceTest} 范式(@MockitoBean 注入
 * Hs300BenchmarkProvider + MarketDataSource 避免真实网络,@Transactional 回滚隔离)。
 *
 * <p>验证两条路径:
 * <ol>
 *   <li>成功:test 集 passed=true → createDraft(最优参数)+ calibrate,落库新策略(状态 CALIBRATED 或 CALIBRATION_FAILED)</li>
 *   <li>失败:test 集 passed=false → 抛 OPTIMIZATION_NO_VALID_PARAMS,不创建任何草稿</li>
 * </ol>
 *
 * <p>诊断增强(issue #28):失败时 message 携带归因维度 + test 策略收益/回撤 vs 三基准收益,
 * 供区分"真过拟合/门槛方差/regime 不匹配"三种失败模式。
 */
class StrategyOptimizeServiceTest extends AbstractIntegrationTest {

    @MockitoBean
    Hs300BenchmarkProvider hs300BenchmarkProvider;

    @MockitoBean
    MarketDataSource marketDataSource;

    @Autowired
    StrategyOptimizeService strategyOptimizeService;

    @Autowired
    FundStrategyRepository fundStrategyRepository;

    @Autowired
    FundRepository fundRepository;

    @Autowired
    FundNavHistoryRepository fundNavHistoryRepository;

    @Test
    @Transactional
    void 寻优成功_test集passed_true_落库新策略() {
        FundEntity fund = persistBroadFund();
        // 震荡上行净值:每 30 天一个 V 型(跌 10% 再升 12%),整体上行。
        // train/test 两段都含跌→升,让策略在低位加仓获利、产生回撤,且 test 集能跑赢 all-in
        persistOscillatingUpNav(fund, 260);

        // mock 沪深300 收益极低(让策略容易跑赢),回撤正常
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("-0.5"), new BigDecimal("0.5")));
        when(marketDataSource.fetchIndexKline(anyString(), anyString())).thenReturn(null);

        Long strategyId = strategyOptimizeService.optimize(fund.getId());

        // 落库了新策略(test 达标 → createDraft + calibrate)
        assertThat(strategyId).isNotNull();
        assertThat(fundStrategyRepository.existsById(strategyId)).isTrue();
        // 状态分离:CALIBRATED 或 CALIBRATION_FAILED 都接受(ADR-0007,test 达标但全窗口可能未过)
        var saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getStatus().name()).isIn("CALIBRATED", "CALIBRATION_FAILED");
    }

    @Test
    @Transactional
    void 寻优失败_test集passed_false_抛异常且不落库() {
        FundEntity fund = persistBroadFund();
        persistOscillatingUpNav(fund, 260);
        // mock 沪深300 收益极高(策略跑不赢 → test 集 passed=false)
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("100"), new BigDecimal("0.01")));
        when(marketDataSource.fetchIndexKline(anyString(), anyString())).thenReturn(null);

        long before = fundStrategyRepository.count();

        assertThatThrownBy(() -> strategyOptimizeService.optimize(fund.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS.name());
        // 未落任何草稿
        assertThat(fundStrategyRepository.count()).isEqualTo(before);
    }

    @Test
    @Transactional
    void 寻优失败_message携带候选数与最优组诊断() {
        FundEntity fund = persistBroadFund();
        persistOscillatingUpNav(fund, 260);
        // mock 沪深300 收益极高 → 全部候选必命中 TEST_RETURN_BELOW_HS300
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("100"), new BigDecimal("0.01")));
        when(marketDataSource.fetchIndexKline(anyString(), anyString())).thenReturn(null);

        long before = fundStrategyRepository.count();

        BusinessException ex = catchOptimizeException(fund.getId());

        // code 不变(契约不变)
        assertThat(ex.getCode()).isEqualTo(ErrorCode.OPTIMIZATION_NO_VALID_PARAMS.name());
        // message 含候选数 + 最优组 train Calmar + test 指标对比
        assertThat(ex.getMessage()).contains("组候选").contains("最优组").contains("train Calmar");
        assertThat(ex.getMessage()).contains("test 策略收益").contains("原因");
        // 原因含 HS300(hs300 收益 100 策略必输)
        assertThat(ex.getMessage()).contains("TEST_RETURN_BELOW_HS300");
        // 未落任何草稿
        assertThat(fundStrategyRepository.count()).isEqualTo(before);
    }

    @Test
    @Transactional
    void 寻优失败_topk全部未达标_message含候选总数() {
        FundEntity fund = persistBroadFund();
        persistOscillatingUpNav(fund, 260);
        // hs300 收益极高 → top-5 全部未达标
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("100"), new BigDecimal("0.01")));
        when(marketDataSource.fetchIndexKline(anyString(), anyString())).thenReturn(null);

        BusinessException ex = catchOptimizeException(fund.getId());

        // message 含候选总数(应 ≤ top-k=5,取决于 train 集有多少候选有回撤)
        assertThat(ex.getMessage()).contains("组候选 test 集全部未达标");
        // 至少 1 组候选
        String countPart = ex.getMessage().substring(
                ex.getMessage().indexOf("寻优 ") + "寻优 ".length(),
                ex.getMessage().indexOf(" 组候选"));
        int count = Integer.parseInt(countPart.trim());
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    /** 跑 optimize 捕获 BusinessException(避免每个测试重复 try/catch 模板)。 */
    private BusinessException catchOptimizeException(Long fundId) {
        try {
            strategyOptimizeService.optimize(fundId);
            throw new AssertionError("期望抛 BusinessException 但未抛");
        } catch (BusinessException ex) {
            return ex;
        }
    }

    private FundEntity persistBroadFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("测试沪深300ETF");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setFundSubType(FundSubType.ETF);
        fund.setPlannedTotalAmount(new BigDecimal("10000"));
        return fundRepository.save(fund);
    }

    /** 构造震荡上行净值:每 30 天一个 V 型(前 15 天跌 10%,后 15 天升 12%),整体上行。
     *  train/test 两段都含多个完整 V 型,让策略在低位加仓获利并产生回撤。 */
    private void persistOscillatingUpNav(FundEntity fund, int totalDays) {
        Instant start = Instant.now().minus(totalDays + 10, ChronoUnit.DAYS);
        BigDecimal level = BigDecimal.ONE;
        for (int i = 0; i < totalDays; i++) {
            int phase = i % 30;
            BigDecimal nav;
            if (phase < 15) {
                // 跌段:从 V 起点跌 10%(线性)
                nav = level.multiply(BigDecimal.valueOf(1.0 - 0.10 * phase / 15));
            } else {
                // 升段:从 V 谷底升 12%(线性),终点比 V 起点高 ~0.8%
                nav = level.multiply(BigDecimal.valueOf(0.90 + 0.12 * (phase - 15) / 15));
            }
            persistNav(fund, start.plus(i, ChronoUnit.DAYS), nav);
            if (phase == 29) {
                // V 结束,更新 level 为该 V 终点(略高于起点,整体上行)
                level = level.multiply(BigDecimal.valueOf(0.90 + 0.12));
            }
        }
    }

    private void persistNav(FundEntity fund, Instant date, BigDecimal nav) {
        FundNavHistoryEntity entity = new FundNavHistoryEntity();
        entity.setFundEntity(fund);
        entity.setNavDate(date);
        entity.setNav(nav);
        entity.setAccumulatedNav(nav);
        fundNavHistoryRepository.save(entity);
    }
}
