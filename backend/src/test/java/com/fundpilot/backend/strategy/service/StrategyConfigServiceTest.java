package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.exception.IllegalStateTransitionException;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundStrategyActivationEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.TakeProfitPhase;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundStrategyActivationRepository;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StrategyConfigService 基础 CRUD + 状态机管理。
 * <p>金字塔加仓退场 + 回测/寻优移除后,状态机简化为:
 * PENDING_CALIBRATION --activate--> EFFECTIVE;updateDraft 改参数(非 EFFECTIVE 态可改)。
 * 不再有 calibrate/CALIBRATED/CALIBRATION_FAILED 流转——createDraft 后可直接 activate 生效。
 */
class StrategyConfigServiceTest extends AbstractIntegrationTest {

    @Autowired
    StrategyConfigService strategyConfigService;

    @Autowired
    FundStrategyRepository fundStrategyRepository;

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
        assertThat(saved.getStopLossPullbackPercent()).isEqualByComparingTo(new BigDecimal("0.08"));
        assertThat(saved.isCustomized()).isTrue();
    }

    @Test
    @Transactional
    void updateDraft_PENDING_CALIBRATION_状态可改() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());

        strategyConfigService.updateDraft(strategyId, configRequest("0.05"));

        FundStrategyEntity saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getStopLossPullbackPercent()).isEqualByComparingTo(new BigDecimal("0.05"));
        // 改参数后状态仍是 PENDING_CALIBRATION
        assertThat(saved.getStatus()).isEqualTo(StrategyParamStatus.PENDING_CALIBRATION);
    }

    @Test
    @Transactional
    void updateDraft_EFFECTIVE_状态抛_IllegalStateTransition() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        // 激活为 EFFECTIVE
        strategyConfigService.activate(strategyId);

        assertThatThrownBy(() -> strategyConfigService.updateDraft(strategyId, configRequest("0.10")))
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

    // ===== activate =====

    @Test
    @Transactional
    void activate_PENDING_CALIBRATION_直接跃迁_EFFECTIVE() {
        FundEntity fund = persistFund();
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());

        strategyConfigService.activate(strategyId);

        FundStrategyEntity saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(StrategyParamStatus.EFFECTIVE);
        assertThat(saved.getTakeProfitPhase()).isEqualTo(TakeProfitPhase.ACCUMULATING);
        // 激活表写了一行,deactivatedAt 为 null
        FundStrategyActivationEntity activation = fundStrategyActivationRepository
                .findByFundStrategyEntity_IdAndDeactivatedAtIsNull(strategyId).orElseThrow();
        assertThat(activation.getDeactivatedAt()).isNull();
    }

    @Test
    @Transactional
    void activate_新版本激活后_旧_EFFECTIVE_回退_PENDING_CALIBRATION_且激活表回填() {
        FundEntity fund = persistFund();
        // 旧版本:激活为 EFFECTIVE
        Long oldId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        strategyConfigService.activate(oldId);

        // 新版本:直接激活
        Long newId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        strategyConfigService.activate(newId);

        // 旧版本回退 PENDING_CALIBRATION
        FundStrategyEntity oldStrategy = fundStrategyRepository.findById(oldId).orElseThrow();
        assertThat(oldStrategy.getStatus()).isEqualTo(StrategyParamStatus.PENDING_CALIBRATION);
        // 旧版本激活表 deactivatedAt 已回填
        FundStrategyActivationEntity oldActivation = fundStrategyActivationRepository
                .findByFundStrategyEntity_IdAndDeactivatedAtIsNull(oldId).orElse(null);
        assertThat(oldActivation).isNull(); // 旧任期已停用,查不到未停用的
        // 新版本激活表未停用
        assertThat(fundStrategyActivationRepository
                .findByFundStrategyEntity_IdAndDeactivatedAtIsNull(newId)).isPresent();
    }

    // ===== retire + CLEARED 全员回退 =====

    @Test
    @Transactional
    void retire_EFFECTIVE_回退_PENDING_CALIBRATION_且激活表回填() {
        FundEntity fund = persistFund();
        Long strategyId = activateStrategy(fund);

        strategyConfigService.retire(strategyId);

        FundStrategyEntity saved = fundStrategyRepository.findById(strategyId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(StrategyParamStatus.PENDING_CALIBRATION);
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
        Long oldId = activateStrategy(fund);
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

    /** 辅助:创建 + 激活一个策略,返回 strategyId。 */
    private Long activateStrategy(FundEntity fund) {
        Long strategyId = strategyConfigService.createDraft(fund.getId(), sampleRequest());
        strategyConfigService.activate(strategyId);
        return strategyId;
    }

    private FundEntity persistFund() {
        FundEntity fund = new FundEntity();
        fund.setOwnerId(testActorId());
        fund.setFundCode("161725");
        fund.setFundName("测试基金");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        return fundRepository.save(fund);
    }

    private static StrategyConfigRequest sampleRequest() {
        return configRequest("0.08");
    }

    private static StrategyConfigRequest configRequest(String stopLossPullbackPercent) {
        return new StrategyConfigRequest(
                new BigDecimal("0.15"),
                new BigDecimal(stopLossPullbackPercent),
                new BigDecimal("0.50"),
                new BigDecimal("0.50"),
                new BigDecimal("0.20"),
                10);
    }
}
