package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.dca.controller.DcaPlanRequest;
import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.exception.IllegalStateTransitionException;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DcaPlanService CRUD + 状态机(DRAFT → activate → EFFECTIVE → retire → DRAFT)。
 * <p>镜像 StrategyConfigServiceTest 的断言模式。同基金同时最多一份 EFFECTIVE:
 * 新版本激活时旧 EFFECTIVE 回退 DRAFT。
 */
class DcaPlanServiceTest extends AbstractIntegrationTest {

    @Autowired
    DcaPlanService dcaPlanService;

    @Autowired
    FundDcaPlanRepository fundDcaPlanRepository;

    @Autowired
    FundRepository fundRepository;

    @Test
    @Transactional
    void createDraft_写入_DRAFT_计划() {
        FundEntity fund = persistFund();

        Long planId = dcaPlanService.createDraft(fund.getId(), weeklyRequest());

        FundDcaPlanEntity saved = fundDcaPlanRepository.findById(planId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(DcaPlanStatus.DRAFT);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(saved.getFrequency()).isEqualTo(DcaFrequency.WEEKLY);
        assertThat(saved.getDayOfWeek()).isEqualTo(1);
    }

    @Test
    @Transactional
    void updateDraft_DRAFT_状态可改() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.createDraft(fund.getId(), weeklyRequest());

        dcaPlanService.updateDraft(planId, new DcaPlanRequest(
                false, new BigDecimal("2000"), DcaFrequency.MONTHLY, null, 15));

        FundDcaPlanEntity saved = fundDcaPlanRepository.findById(planId).orElseThrow();
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(saved.getFrequency()).isEqualTo(DcaFrequency.MONTHLY);
        assertThat(saved.getDayOfMonth()).isEqualTo(15);
        assertThat(saved.getEnabled()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(DcaPlanStatus.DRAFT);
    }

    @Test
    @Transactional
    void updateDraft_EFFECTIVE_状态抛_IllegalStateTransition() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.createDraft(fund.getId(), weeklyRequest());
        dcaPlanService.activate(planId);

        assertThatThrownBy(() -> dcaPlanService.updateDraft(planId, weeklyRequest(new BigDecimal("2000"))))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @Transactional
    void listByFund_返回该基金所有计划版本() {
        FundEntity fund = persistFund();
        dcaPlanService.createDraft(fund.getId(), weeklyRequest());
        dcaPlanService.createDraft(fund.getId(), monthlyRequest());

        var list = dcaPlanService.listByFund(fund.getId());

        assertThat(list).hasSize(2);
    }

    @Test
    @Transactional
    void findActive_无_EFFECTIVE_返回_empty() {
        FundEntity fund = persistFund();
        dcaPlanService.createDraft(fund.getId(), weeklyRequest());

        Optional<FundDcaPlanEntity> active = dcaPlanService.findActive(fund.getId());

        assertThat(active).isEmpty();
    }

    @Test
    @Transactional
    void activate_DRAFT_直接跃迁_EFFECTIVE() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.createDraft(fund.getId(), weeklyRequest());

        dcaPlanService.activate(planId);

        FundDcaPlanEntity saved = fundDcaPlanRepository.findById(planId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(DcaPlanStatus.EFFECTIVE);
    }

    @Test
    @Transactional
    void activate_新版本激活后_旧_EFFECTIVE_回退_DRAFT() {
        FundEntity fund = persistFund();
        Long oldId = dcaPlanService.createDraft(fund.getId(), weeklyRequest());
        dcaPlanService.activate(oldId);

        Long newId = dcaPlanService.createDraft(fund.getId(), monthlyRequest());
        dcaPlanService.activate(newId);

        FundDcaPlanEntity oldPlan = fundDcaPlanRepository.findById(oldId).orElseThrow();
        assertThat(oldPlan.getStatus()).isEqualTo(DcaPlanStatus.DRAFT);
        FundDcaPlanEntity newPlan = fundDcaPlanRepository.findById(newId).orElseThrow();
        assertThat(newPlan.getStatus()).isEqualTo(DcaPlanStatus.EFFECTIVE);
        // 同基金最多一份 EFFECTIVE
        assertThat(fundDcaPlanRepository.findByFundEntity_IdAndStatus(fund.getId(), DcaPlanStatus.EFFECTIVE))
                .contains(newPlan);
    }

    @Test
    @Transactional
    void retire_EFFECTIVE_回退_DRAFT() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.createDraft(fund.getId(), weeklyRequest());
        dcaPlanService.activate(planId);

        dcaPlanService.retire(planId);

        FundDcaPlanEntity saved = fundDcaPlanRepository.findById(planId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(DcaPlanStatus.DRAFT);
    }

    @Test
    @Transactional
    void retire_非_EFFECTIVE_状态抛_IllegalStateTransition() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.createDraft(fund.getId(), weeklyRequest());

        assertThatThrownBy(() -> dcaPlanService.retire(planId))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    private FundEntity persistFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("161725");
        fund.setFundName("测试基金");
        return fundRepository.save(fund);
    }

    private static DcaPlanRequest weeklyRequest() {
        return weeklyRequest(new BigDecimal("1000"));
    }

    private static DcaPlanRequest weeklyRequest(BigDecimal amount) {
        return new DcaPlanRequest(true, amount, DcaFrequency.WEEKLY, 1, null);
    }

    private static DcaPlanRequest monthlyRequest() {
        return new DcaPlanRequest(true, new BigDecimal("1000"), DcaFrequency.MONTHLY, null, 15);
    }
}
