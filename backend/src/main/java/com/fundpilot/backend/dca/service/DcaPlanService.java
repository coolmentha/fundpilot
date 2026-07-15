package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.dca.controller.DcaPlanRequest;
import com.fundpilot.backend.dca.controller.DcaPlanManagementView;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final DcaPlanForecastService dcaPlanForecastService;

    @Transactional
    public Long create(Long fundId, DcaPlanRequest request) {
        FundEntity fund = fundRepository.findByIdForUpdate(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + fundId + " 不存在"));
        // 新建即激活:同基金已有 EFFECTIVE 计划则回退为 DRAFT(同基金同时最多一份 EFFECTIVE)
        demoteExistingEffective(fundId);
        FundDcaPlanEntity plan = new FundDcaPlanEntity();
        plan.setFundEntity(fund);
        plan.setStatus(DcaPlanStatus.EFFECTIVE);
        plan.setEnabled(true);
        applyRequest(plan, request);
        validateAndNormalize(plan);
        return fundDcaPlanRepository.save(plan).getId();
    }

    @Transactional
    public void update(Long planId, DcaPlanRequest request) {
        FundDcaPlanEntity plan = requirePlan(planId);
        applyRequest(plan, request);
        validateAndNormalize(plan);
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
    public List<DcaPlanManagementView> listManagementView() {
        List<FundDcaPlanEntity> plans = fundDcaPlanRepository.findAllWithFund().stream()
                .sorted(Comparator
                        .comparing((FundDcaPlanEntity plan) -> plan.getStatus() != DcaPlanStatus.EFFECTIVE)
                        .thenComparing(plan -> !Boolean.TRUE.equals(plan.getEnabled()))
                        .thenComparing(plan -> plan.getFundEntity().getFundName())
                        .thenComparing(FundDcaPlanEntity::getId))
                .toList();
        Map<Long, List<Instant>> datesByPlan = dcaPlanForecastService.currentMonthExecutionDates(plans);
        return plans.stream()
                .map(plan -> DcaPlanManagementView.from(
                        plan, datesByPlan.getOrDefault(plan.getId(), List.of())))
                .toList();
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
        fundRepository.findByIdForUpdate(plan.getFundEntity().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND,
                        "Fund #" + plan.getFundEntity().getId() + " 不存在"));
        validateAndNormalize(plan);
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

    @Transactional
    public void setEnabled(Long planId, boolean enabled) {
        FundDcaPlanEntity plan = requirePlan(planId);
        if (plan.getStatus() != DcaPlanStatus.EFFECTIVE) {
            throw new IllegalStateTransitionException(plan.getStatus().name(), "EFFECTIVE(暂停/恢复)");
        }
        plan.setEnabled(enabled);
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
        if (request == null) {
            throw new BusinessException(ErrorCode.DCA_PLAN_INVALID, "定投计划参数不能为空");
        }
        if (request.enabled() != null) plan.setEnabled(request.enabled());
        if (request.amount() != null) plan.setAmount(request.amount());
        if (request.frequency() != null) plan.setFrequency(request.frequency());
        if (request.dayOfWeek() != null) plan.setDayOfWeek(request.dayOfWeek());
        if (request.dayOfMonth() != null) plan.setDayOfMonth(request.dayOfMonth());
    }

    private void validateAndNormalize(FundDcaPlanEntity plan) {
        if (plan.getAmount() == null || plan.getAmount().signum() <= 0) {
            throw new BusinessException(ErrorCode.DCA_PLAN_INVALID, "每次定投金额必须大于 0");
        }
        if (plan.getFrequency() == null) {
            throw new BusinessException(ErrorCode.DCA_PLAN_INVALID, "定投频率不能为空");
        }
        switch (plan.getFrequency()) {
            case DAILY -> {
                plan.setDayOfWeek(null);
                plan.setDayOfMonth(null);
            }
            case WEEKLY -> {
                if (plan.getDayOfWeek() == null || plan.getDayOfWeek() < 1 || plan.getDayOfWeek() > 5) {
                    throw new BusinessException(ErrorCode.DCA_PLAN_INVALID, "周定投日必须为周一至周五");
                }
                plan.setDayOfMonth(null);
            }
            case MONTHLY -> {
                if (plan.getDayOfMonth() == null || plan.getDayOfMonth() < 1 || plan.getDayOfMonth() > 28) {
                    throw new BusinessException(ErrorCode.DCA_PLAN_INVALID, "月定投日必须在 1 至 28 日之间");
                }
                plan.setDayOfWeek(null);
            }
        }
    }
}
