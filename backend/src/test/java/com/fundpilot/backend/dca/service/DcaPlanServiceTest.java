package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.dca.controller.DcaPlanRequest;
import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.exception.IllegalStateTransitionException;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DcaPlanService CRUD + 状态机。
 * <p>新建即激活:create 直接落 EFFECTIVE(同基金已有 EFFECTIVE 则回退 DRAFT)。
 * 状态流转:EFFECTIVE --retire--> DRAFT --activate--> EFFECTIVE。同基金同时最多一份 EFFECTIVE。
 */
class DcaPlanServiceTest extends AbstractIntegrationTest {

    @Autowired
    DcaPlanService dcaPlanService;

    @Autowired
    FundDcaPlanRepository fundDcaPlanRepository;

    @Autowired
    FundRepository fundRepository;

    @Autowired
    FundTransactionRepository fundTransactionRepository;

    @Test
    @Transactional
    void create_新建即激活为_EFFECTIVE() {
        FundEntity fund = persistFund();

        Long planId = dcaPlanService.create(fund.getId(), weeklyRequest());

        FundDcaPlanEntity saved = fundDcaPlanRepository.findById(planId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(DcaPlanStatus.EFFECTIVE);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(saved.getFrequency()).isEqualTo(DcaFrequency.WEEKLY);
        assertThat(saved.getDayOfWeek()).isEqualTo(1);
    }

    @Test
    @Transactional
    void update_DRAFT_状态可改() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.create(fund.getId(), weeklyRequest());
        dcaPlanService.retire(planId); // EFFECTIVE → DRAFT 才可改

        dcaPlanService.update(planId, new DcaPlanRequest(
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
    void update_EFFECTIVE_状态可直接修改且保持生效() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.create(fund.getId(), weeklyRequest()); // 新建即 EFFECTIVE

        dcaPlanService.update(planId, weeklyRequest(new BigDecimal("2000")));

        FundDcaPlanEntity saved = fundDcaPlanRepository.findById(planId).orElseThrow();
        assertThat(saved.getAmount()).isEqualByComparingTo("2000");
        assertThat(saved.getStatus()).isEqualTo(DcaPlanStatus.EFFECTIVE);
    }

    @Test
    @Transactional
    void listByFund_返回该基金所有计划版本() {
        FundEntity fund = persistFund();
        dcaPlanService.create(fund.getId(), weeklyRequest());
        dcaPlanService.create(fund.getId(), monthlyRequest());

        var list = dcaPlanService.listByFund(fund.getId());

        assertThat(list).hasSize(2);
    }

    @Test
    @Transactional
    void findActive_无_EFFECTIVE_返回_empty() {
        FundEntity fund = persistFund();

        Optional<FundDcaPlanEntity> active = dcaPlanService.findActive(fund.getId());

        assertThat(active).isEmpty();
    }

    @Test
    @Transactional
    void activate_DRAFT_跃迁_EFFECTIVE() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.create(fund.getId(), weeklyRequest());
        dcaPlanService.retire(planId); // → DRAFT

        dcaPlanService.activate(planId);

        FundDcaPlanEntity saved = fundDcaPlanRepository.findById(planId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(DcaPlanStatus.EFFECTIVE);
    }

    @Test
    @Transactional
    void create_新版本新建后_旧_EFFECTIVE_回退_DRAFT() {
        FundEntity fund = persistFund();
        Long oldId = dcaPlanService.create(fund.getId(), weeklyRequest()); // 新建即 EFFECTIVE

        Long newId = dcaPlanService.create(fund.getId(), monthlyRequest()); // 旧 EFFECTIVE 回退 DRAFT

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
        Long planId = dcaPlanService.create(fund.getId(), weeklyRequest());

        dcaPlanService.retire(planId);

        FundDcaPlanEntity saved = fundDcaPlanRepository.findById(planId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(DcaPlanStatus.DRAFT);
    }

    @Test
    @Transactional
    void retire_非_EFFECTIVE_状态抛_IllegalStateTransition() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.create(fund.getId(), weeklyRequest());
        dcaPlanService.retire(planId); // → DRAFT

        assertThatThrownBy(() -> dcaPlanService.retire(planId))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    @Transactional
    void setEnabled_暂停和恢复不改变_EFFECTIVE_状态() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.create(fund.getId(), weeklyRequest());

        dcaPlanService.setEnabled(planId, false);
        FundDcaPlanEntity paused = fundDcaPlanRepository.findById(planId).orElseThrow();
        assertThat(paused.getEnabled()).isFalse();
        assertThat(paused.getStatus()).isEqualTo(DcaPlanStatus.EFFECTIVE);

        dcaPlanService.setEnabled(planId, true);
        assertThat(fundDcaPlanRepository.findById(planId).orElseThrow().getEnabled()).isTrue();
    }

    @Test
    @Transactional
    void create_周末计划日_拒绝创建() {
        FundEntity fund = persistFund();

        assertThatThrownBy(() -> dcaPlanService.create(fund.getId(),
                new DcaPlanRequest(true, new BigDecimal("1000"), DcaFrequency.WEEKLY, 6, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.DCA_PLAN_INVALID.name());
    }

    @Test
    @Transactional
    void create_非正金额_拒绝创建() {
        FundEntity fund = persistFund();

        assertThatThrownBy(() -> dcaPlanService.create(fund.getId(),
                new DcaPlanRequest(true, BigDecimal.ZERO, DcaFrequency.WEEKLY, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.DCA_PLAN_INVALID.name());
    }

    @Test
    @Transactional
    void delete_DRAFT_软删除计划且保留历史交易() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.create(fund.getId(), weeklyRequest());
        dcaPlanService.retire(planId);
        FundTransactionEntity transaction = new FundTransactionEntity();
        transaction.setFundEntity(fund);
        transaction.setDcaPlanId(planId);
        transaction.setSource(FundTransactionSource.INVEST);
        transaction.setStatus(FundTransactionStatus.CONFIRMED);
        transaction.setTradeDate(Instant.parse("2026-07-14T00:00:00Z"));
        Long transactionId = fundTransactionRepository.save(transaction).getId();

        dcaPlanService.delete(planId);

        assertThat(fundDcaPlanRepository.findById(planId)).isEmpty();
        assertThat(fundTransactionRepository.findById(transactionId)).get()
                .extracting(FundTransactionEntity::getDcaPlanId).isEqualTo(planId);
    }

    @Test
    @Transactional
    void delete_EFFECTIVE_拒绝并返回专用错误码() {
        FundEntity fund = persistFund();
        Long planId = dcaPlanService.create(fund.getId(), weeklyRequest());

        assertThatThrownBy(() -> dcaPlanService.delete(planId))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.DCA_PLAN_DELETE_REQUIRES_DRAFT.name());
        assertThat(fundDcaPlanRepository.findById(planId)).isPresent();
    }

    @Test
    @Transactional
    void delete_不存在计划_返回_NOT_FOUND() {
        assertThatThrownBy(() -> dcaPlanService.delete(Long.MAX_VALUE))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.DCA_PLAN_NOT_FOUND.name());
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
