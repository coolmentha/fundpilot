package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.dca.controller.DcaPlanRequest;
import com.fundpilot.backend.dca.controller.FundDcaPlanView;
import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.exception.IllegalStateTransitionException;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 定投计划服务:CRUD + 状态机。
 * <p>新建即激活:create 直接落 EFFECTIVE(同基金已有 EFFECTIVE 则回退为 DRAFT)。
 * 状态流转:EFFECTIVE --retire--> DRAFT --activate--> EFFECTIVE。EFFECTIVE 计划由 DcaSuggestionJob 在定投日自动生成 INVEST 交易。
 * 同基金同时最多一份 EFFECTIVE(数据库 uq_fund_dca_plan_effective 兜底)。
 */
@Service
@RequiredArgsConstructor
public class DcaPlanService {

    private final FundDcaPlanRepository fundDcaPlanRepository;
    private final FundRepository fundRepository;

    @Transactional
    public Long create(Long fundId, DcaPlanRequest request) {
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + fundId + " 不存在"));
        // 新建即激活:同基金已有 EFFECTIVE 计划则回退为 DRAFT(同基金同时最多一份 EFFECTIVE)
        demoteExistingEffective(fundId);
        FundDcaPlanEntity plan = new FundDcaPlanEntity();
        plan.setFundEntity(fund);
        plan.setStatus(DcaPlanStatus.EFFECTIVE);
        plan.setEnabled(true);
        applyRequest(plan, request);
        return fundDcaPlanRepository.save(plan).getId();
    }

    @Transactional
    public void updateDraft(Long planId, DcaPlanRequest request) {
        FundDcaPlanEntity plan = requirePlan(planId);
        if (plan.getStatus() == DcaPlanStatus.EFFECTIVE) {
            throw new IllegalStateTransitionException(plan.getStatus().name(), "草稿(非生效态,可改参数)");
        }
        applyRequest(plan, request);
        fundDcaPlanRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public List<FundDcaPlanEntity> listByFund(Long fundId) {
        return fundDcaPlanRepository.findByFundEntity_Id(fundId);
    }

    @Transactional(readOnly = true)
    public List<FundDcaPlanView> listByFundView(Long fundId) {
        return listByFund(fundId).stream().map(FundDcaPlanView::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<FundDcaPlanEntity> findActive(Long fundId) {
        return fundDcaPlanRepository.findByFundEntity_IdAndStatus(fundId, DcaPlanStatus.EFFECTIVE);
    }

    @Transactional(readOnly = true)
    public Optional<FundDcaPlanView> findActiveView(Long fundId) {
        return findActive(fundId).map(FundDcaPlanView::from);
    }

    @Transactional
    public void activate(Long planId) {
        FundDcaPlanEntity plan = requirePlan(planId);
        if (plan.getStatus() == DcaPlanStatus.EFFECTIVE) {
            return;
        }
        demoteExistingEffective(plan.getFundEntity().getId());
        plan.setStatus(DcaPlanStatus.EFFECTIVE);
        fundDcaPlanRepository.save(plan);
    }

    @Transactional
    public void retire(Long planId) {
        FundDcaPlanEntity plan = requirePlan(planId);
        if (plan.getStatus() != DcaPlanStatus.EFFECTIVE) {
            throw new IllegalStateTransitionException(plan.getStatus().name(), "DRAFT(停用)");
        }
        plan.setStatus(DcaPlanStatus.DRAFT);
        fundDcaPlanRepository.save(plan);
    }

    private FundDcaPlanEntity requirePlan(Long planId) {
        return fundDcaPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DCA_PLAN_NOT_FOUND, "DcaPlan #" + planId + " 不存在"));
    }

    /** 同基金已有 EFFECTIVE 计划则回退为 DRAFT(保证同时最多一份 EFFECTIVE)。
     *  用 saveAndFlush 强制先 UPDATE,避免随后新建 EFFECTIVE 的 INSERT 先于 UPDATE 触发 uq_fund_dca_plan_effective 冲突。 */
    private void demoteExistingEffective(Long fundId) {
        fundDcaPlanRepository.findByFundEntity_IdAndStatus(fundId, DcaPlanStatus.EFFECTIVE)
                .ifPresent(old -> {
                    old.setStatus(DcaPlanStatus.DRAFT);
                    fundDcaPlanRepository.saveAndFlush(old);
                });
    }

    private void applyRequest(FundDcaPlanEntity plan, DcaPlanRequest request) {
        if (request.enabled() != null) plan.setEnabled(request.enabled());
        if (request.amount() != null) plan.setAmount(request.amount());
        if (request.frequency() != null) plan.setFrequency(request.frequency());
        if (request.dayOfWeek() != null) plan.setDayOfWeek(request.dayOfWeek());
        if (request.dayOfMonth() != null) plan.setDayOfMonth(request.dayOfMonth());
    }
}
