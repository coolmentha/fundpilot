package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.IllegalStateTransitionException;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundStrategyActivationEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundStrategyActivationRepository;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.repository.StrategyBacktestRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * issue #10 循环 A:{@code StrategyConfigService} 基础 CRUD + updateDraft 状态校验。
 * <p>createDraft 写 PENDING_CALIBRATION;updateDraft 仅 PENDING_CALIBRATION 可改,
 * CALIBRATED/EFFECTIVE 抛 {@link IllegalStateTransitionException}。
 */
class StrategyConfigServiceTest extends AbstractIntegrationTest {

    @MockitoBean
    StrategyBacktestService strategyBacktestService;

    @Autowired
    StrategyConfigService strategyConfigService;

    @Autowired
    FundStrategyRepository fundStrategyRepository;

    @Autowired
    StrategyBacktestRepository strategyBacktestRepository;

    @Autowired
    FundStrategyActivationRepository fundStrategyActivationRepository;

    @Autowired
    FundRepository fundRepository;

    @Test
    @Transactional
    void createDraft_写入_PENDING_CALIBRATION_策略() {
        FundEntity fund = persistFund();

        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());

        FundStrategyEntity saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(StrategyParamStatus.PENDING_CALIBRATION);
        assertThat(saved.getTier1Drawdown()).isEqualByComparingTo(new BigDecimal("0.05"));
        assertThat(saved.getTier1Ratio()).isEqualByComparingTo(new BigDecimal("0.30"));
    }

    @Test
    @Transactional
    void updateDraft_PENDING_CALIBRATION_状态可改() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());

        strategyConfigService.updateDraft(strategyId, configRequest("0.08", "0.40"));

        FundStrategyEntity saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getTier1Drawdown()).isEqualByComparingTo(new BigDecimal("0.08"));
        assertThat(saved.getTier1Ratio()).isEqualByComparingTo(new BigDecimal("0.40"));
        // 改参数后状态仍是 PENDING_CALIBRATION
        assertThat(saved.getStatus()).isEqualTo(StrategyParamStatus.PENDING_CALIBRATION);
    }

    @Test
    @Transactional
    void updateDraft_CALIBRATED_状态抛_IllegalStateTransition() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        // 手动置为 CALIBRATED 模拟已校准
        FundStrategyEntity strategy = fundStrategyRepository.findById(strategyId).orElseThrow();
        strategy.setStatus(StrategyParamStatus.CALIBRATED);
        fundStrategyRepository.save(strategy);

        assertThatThrownBy(() -> strategyConfigService.updateDraft(strategyId, configRequest("0.10", "0.50")))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @Transactional
    void listByFund_返回该基金所有策略版本() {
        FundEntity fund = persistFund();
        strategyConfigService.createDraft(fund.getId(), sampleRequest());
        strategyConfigService.createDraft(fund.getId(), sampleRequest());

        var list = strategyConfigService.listByFund(fund.getId());

        assertThat(list).hasSize(2);
    }

    @Test
    @Transactional
    void findActive_无_EFFECTIVE_返回_empty() {
        FundEntity fund = persistFund();
        strategyConfigService.createDraft(fund.getId(), sampleRequest());

        var active = strategyConfigService.findActive(fund.getId());

        assertThat(active).isEmpty();
    }

    // ===== 循环 B:calibrate =====

    @Test
    @Transactional
    void calibrate_PENDING_跃迁_CALIBRATED_且调回测留痕() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        // Mock #11.run 返回 passed=true 的回测结果
        when(strategyBacktestService.run(eq(strategyId), any(BacktestWindow.class)))
                .thenReturn(backtestEntity(strategyId, true));

        strategyConfigService.calibrate(strategyId);

        FundStrategyEntity saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(StrategyParamStatus.CALIBRATED);
        // 回测留痕:落了 StrategyBacktestEntity
        assertThat(strategyBacktestRepository.existsByFundStrategyEntity_IdAndPassedTrue(strategyId)).isTrue();
    }

    @Test
    @Transactional
    void calibrate_回测未通过_进_CALIBRATION_FAILED_可校准重测() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        // Mock #11.run 返回 passed=false(收益不达标)
        when(strategyBacktestService.run(eq(strategyId), any(BacktestWindow.class)))
                .thenReturn(backtestEntity(strategyId, false));

        strategyConfigService.calibrate(strategyId);

        FundStrategyEntity saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        // 未通过:进 CALIBRATION_FAILED(不是 CALIBRATED)
        assertThat(saved.getStatus()).isEqualTo(StrategyParamStatus.CALIBRATION_FAILED);
        // 回测结果照样留痕(供前端展示为何不通过)
        assertThat(strategyBacktestRepository.existsByFundStrategyEntity_IdAndPassedTrue(strategyId)).isFalse();
        // 未通过态可再次 calibrate 重测(通过则进 CALIBRATED)
        when(strategyBacktestService.run(eq(strategyId), any(BacktestWindow.class)))
                .thenReturn(backtestEntity(strategyId, true));
        strategyConfigService.calibrate(strategyId);
        assertThat(fundStrategyRepository.findById(strategyId).orElseThrow().getStatus())
                .isEqualTo(StrategyParamStatus.CALIBRATED);
    }

    @Test
    @Transactional
    void updateDraft_CALIBRATION_FAILED_改参数回退_PENDING_CALIBRATION() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        when(strategyBacktestService.run(eq(strategyId), any(BacktestWindow.class)))
                .thenReturn(backtestEntity(strategyId, false));
        strategyConfigService.calibrate(strategyId); // 进 CALIBRATION_FAILED

        // 未通过态改参数 → 回退 PENDING_CALIBRATION(旧回测基于旧参数,已失效)
        strategyConfigService.updateDraft(strategyId, configRequest("0.08", "0.40"));

        FundStrategyEntity saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(StrategyParamStatus.PENDING_CALIBRATION);
        assertThat(saved.getTier1Drawdown()).isEqualByComparingTo(new BigDecimal("0.08"));
    }

    @Test
    @Transactional
    void calibrate_CALIBRATED_状态抛_IllegalStateTransition() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        FundStrategyEntity strategy = fundStrategyRepository.findById(strategyId).orElseThrow();
        strategy.setStatus(StrategyParamStatus.CALIBRATED);
        fundStrategyRepository.save(strategy);

        assertThatThrownBy(() -> strategyConfigService.calibrate(strategyId))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @Transactional
    void getBacktestResult_返回最近一次回测() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        when(strategyBacktestService.run(eq(strategyId), any(BacktestWindow.class)))
                .thenReturn(backtestEntity(strategyId, true));

        strategyConfigService.calibrate(strategyId);

        var result = strategyConfigService.getBacktestResult(strategyId);
        assertThat(result).isPresent();
        assertThat(result.get().isPassed()).isTrue();
    }

    // ===== 循环 C:activate =====

    @Test
    @Transactional
    void activate_CALIBRATED_且_passed_true_成功跃迁_EFFECTIVE() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        when(strategyBacktestService.run(eq(strategyId), any(BacktestWindow.class)))
                .thenReturn(backtestEntity(strategyId, true));
        strategyConfigService.calibrate(strategyId);

        strategyConfigService.activate(strategyId);

        FundStrategyEntity saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(StrategyParamStatus.EFFECTIVE);
        // 激活表写了一行,deactivatedAt 为 null
        FundStrategyActivationEntity activation = fundStrategyActivationRepository
                .findByFundStrategyEntity_IdAndDeactivatedAtIsNull(strategyId).orElseThrow();
        assertThat(activation.getDeactivatedAt()).isNull();
    }

    @Test
    @Transactional
    void activate_CALIBRATED_但无_passed_true_回测_抛_NO_VALID_BACKTEST() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        // 手动置 CALIBRATED 但不落任何 passed=true 回测(模拟数据异常:已校准却无通过记录)
        FundStrategyEntity strategy = fundStrategyRepository.findById(strategyId).orElseThrow();
        strategy.setStatus(StrategyParamStatus.CALIBRATED);
        fundStrategyRepository.save(strategy);

        assertThatThrownBy(() -> strategyConfigService.activate(strategyId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无 passed=true 的回测");
    }

    @Test
    @Transactional
    void activate_CALIBRATION_FAILED_未通过_抛_IllegalStateTransition() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        when(strategyBacktestService.run(eq(strategyId), any(BacktestWindow.class)))
                .thenReturn(backtestEntity(strategyId, false)); // passed=false
        strategyConfigService.calibrate(strategyId); // 进 CALIBRATION_FAILED

        // 未通过在 CALIBRATION_FAILED,activate 状态门控(只认 CALIBRATED)拦下
        assertThatThrownBy(() -> strategyConfigService.activate(strategyId))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @Transactional
    void activate_新版本激活后_旧_EFFECTIVE_回退_CALIBRATED_且激活表回填() {
        FundEntity fund = persistFund();
        // 旧版本:激活为 EFFECTIVE
        Long oldId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        when(strategyBacktestService.run(eq(oldId), any(BacktestWindow.class)))
                .thenReturn(backtestEntity(oldId, true));
        strategyConfigService.calibrate(oldId);
        strategyConfigService.activate(oldId);

        // 新版本:校准 + 激活
        Long newId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        when(strategyBacktestService.run(eq(newId), any(BacktestWindow.class)))
                .thenReturn(backtestEntity(newId, true));
        strategyConfigService.calibrate(newId);
        strategyConfigService.activate(newId);

        // 旧版本回退 CALIBRATED
        FundStrategyEntity oldStrategy = fundStrategyRepository.findById(oldId).orElseThrow();
        assertThat(oldStrategy.getStatus()).isEqualTo(StrategyParamStatus.CALIBRATED);
        // 旧版本激活表 deactivatedAt 已回填
        FundStrategyActivationEntity oldActivation = fundStrategyActivationRepository
                .findByFundStrategyEntity_IdAndDeactivatedAtIsNull(oldId).orElse(null);
        assertThat(oldActivation).isNull(); // 旧任期已停用,查不到未停用的
        // 新版本激活表未停用
        assertThat(fundStrategyActivationRepository
                .findByFundStrategyEntity_IdAndDeactivatedAtIsNull(newId)).isPresent();
    }

    // ===== 循环 D:retire + CLEARED 全员回退 =====

    @Test
    @Transactional
    void retire_EFFECTIVE_回退_CALIBRATED_且激活表回填() {
        FundEntity fund = persistFund();
        Long strategyId = activatePassedStrategy(fund);

        strategyConfigService.retire(strategyId);

        FundStrategyEntity saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(StrategyParamStatus.CALIBRATED);
        // 激活表 deactivatedAt 已回填(查不到未停用任期)
        assertThat(fundStrategyActivationRepository
                .findByFundStrategyEntity_IdAndDeactivatedAtIsNull(strategyId)).isEmpty();
    }

    @Test
    @Transactional
    void retire_非_EFFECTIVE_状态抛_IllegalStateTransition() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        // 仍是 PENDING_CALIBRATION

        assertThatThrownBy(() -> strategyConfigService.retire(strategyId))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @Transactional
    void 清仓分水岭_CLEARED_到_PENDING_HOLDING_全员回退_PENDING_CALIBRATION() {
        FundEntity fund = persistFund();
        // 旧版本:激活 EFFECTIVE
        Long oldId = activatePassedStrategy(fund);
        // 新草稿:PENDING_CALIBRATION
        Long draftId = strategyConfigService.createDraft(fund.getId(), sampleRequest());

        // 模拟清仓分水岭:FundEntity.status CLEARED → PENDING_HOLDING
        fund.setStatus(FundStatus.CLEARED);
        fundRepository.save(fund);
        fund.setStatus(FundStatus.PENDING_HOLDING);
        fundRepository.save(fund);
        strategyConfigService.onFundClearedToPendingHolding(fund.getId());

        // 所有版本回退 PENDING_CALIBRATION
        FundStrategyEntity oldStrategy = fundStrategyRepository.findById(oldId).orElseThrow();
        assertThat(oldStrategy.getStatus()).isEqualTo(StrategyParamStatus.PENDING_CALIBRATION);
        FundStrategyEntity draftStrategy = fundStrategyRepository.findById(draftId).orElseThrow();
        assertThat(draftStrategy.getStatus()).isEqualTo(StrategyParamStatus.PENDING_CALIBRATION);
        // 激活表所有未停用任期 deactivatedAt 回填
        assertThat(fundStrategyActivationRepository.findByFundEntity_IdAndDeactivatedAtIsNull(fund.getId())).isEmpty();
    }

    /** 辅助:创建 + 校准 + 激活一个 passed=true 的策略,返回 strategyId。 */
    private Long activatePassedStrategy(FundEntity fund) {
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        when(strategyBacktestService.run(eq(strategyId), any(BacktestWindow.class)))
                .thenReturn(backtestEntity(strategyId, true));
        strategyConfigService.calibrate(strategyId);
        strategyConfigService.activate(strategyId);
        return strategyId;
    }

    private StrategyBacktestEntity backtestEntity(Long strategyId, boolean passed) {
        FundStrategyEntity strategy = fundStrategyRepository.findById(strategyId).orElseThrow();
        StrategyBacktestEntity entity = new StrategyBacktestEntity();
        entity.setFundStrategyEntity(strategy);
        entity.setBacktestStartDate(Instant.parse("2025-01-01T00:00:00Z"));
        entity.setBacktestEndDate(Instant.parse("2025-12-31T00:00:00Z"));
        entity.setStrategyReturn(new BigDecimal("0.15"));
        entity.setStrategyMaxDrawdown(new BigDecimal("0.08"));
        entity.setBenchmarkHs300Return(new BigDecimal("0.10"));
        entity.setBenchmarkAllInReturn(new BigDecimal("0.12"));
        entity.setBenchmarkDcaReturn(new BigDecimal("0.08"));
        entity.setBenchmarkHs300MaxDrawdown(new BigDecimal("0.15"));
        entity.setBenchmarkAllInMaxDrawdown(new BigDecimal("0.10"));
        entity.setBenchmarkDcaMaxDrawdown(new BigDecimal("0.09"));
        entity.setPassed(passed);
        return strategyBacktestRepository.save(entity);
    }

    private FundEntity persistFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("161725");
        fund.setFundName("测试基金");
        return fundRepository.save(fund);
    }

    private static StrategyConfigRequest sampleRequest() {
        return configRequest("0.05", "0.30");
    }

    private static StrategyConfigRequest configRequest(String tier1Drawdown, String tier1Ratio) {
        return new StrategyConfigRequest(
                new BigDecimal(tier1Drawdown), new BigDecimal("0.10"), new BigDecimal("0.15"), new BigDecimal("0.20"),
                new BigDecimal(tier1Ratio), new BigDecimal("0.30"), new BigDecimal("0.20"), new BigDecimal("0.20"),
                new BigDecimal("0.05"), new BigDecimal("0.08"));
    }
}
