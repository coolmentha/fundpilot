package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.controller.FundView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.support.FundTypeClassification;
import com.fundpilot.backend.fund.service.support.FundTypeClassifier;
import com.fundpilot.backend.fund.service.support.HardConstraintConfig;
import com.fundpilot.backend.user.service.UserConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 基金服务(issue #16 + ADR-0005):基金 CRUD 业务逻辑,Controller 只做 HTTP 路由,逻辑下沉到本层。
 * <p>新建时类型字段(fundSubType/fundCategory/benchmarkIndexCode)优先用前端从字典搜索带入的值;
 * 缺省时后端按 fundName 兜底跑 {@link FundTypeClassifier} 识别(尽力填+可覆盖,CONTEXT.md「基金类型自动识别」)。
 * 不再调 {@code FundDictBackfillService.backfillAll()} 批量回填——字典搜索已替代该职责。
 * 返回 {@link FundView} DTO,不直接暴露 {@link FundEntity}。
 */
@Service
@RequiredArgsConstructor
public class FundService {

    private final FundRepository fundRepository;
    private final FundArchiveService fundArchiveService;
    private final UserConfigService userConfigService;

    /** 查全部基金。 */
    public List<FundView> list() {
        return fundRepository.findAll().stream().map(FundView::from).toList();
    }

    /**
     * 新建基金;类型字段优先用请求带入值,缺省时按 fundName 兜底识别。
     * <p>fundCode/fundName 二选一即可(CONTEXT.md「基金字典搜索」);两者都缺 → 业务异常。
     */
    @Transactional
    public FundView create(FundCreateRequest request) {
        if ((request.fundCode() == null || request.fundCode().isBlank())
                && (request.fundName() == null || request.fundName().isBlank())) {
            throw new BusinessException(ErrorCode.MISSING_FUND_IDENTITY, "基金代码和名称至少填一个");
        }
        FundEntity fund = new FundEntity();
        fund.setFundCode(request.fundCode());
        fund.setFundName(request.fundName());
        fund.setPlannedTotalAmount(request.plannedTotalAmount());

        // 类型字段:请求带入优先,缺省时按 fundName 兜底识别(尽力填)
        FundTypeClassification fallback = request.fundSubType() == null && request.fundCategory() == null
                ? FundTypeClassifier.classify(request.fundName()) : null;
        fund.setFundSubType(request.fundSubType() != null ? request.fundSubType()
                : (fallback != null ? fallback.fundSubType() : null));
        fund.setFundCategory(request.fundCategory() != null ? request.fundCategory()
                : (fallback != null ? fallback.fundCategory() : null));
        fund.setBenchmarkIndexCode(request.benchmarkIndexCode() != null ? request.benchmarkIndexCode()
                : (fallback != null ? fallback.benchmarkIndexCode() : null));

        validatePlannedTotalAmount(fund.getPlannedTotalAmount(), fund.getFundCategory());
        return FundView.from(fundRepository.save(fund));
    }

    /** 查单个基金;不存在抛 400(业务问题,非路由不存在)。 */
    public FundView get(Long id) {
        return FundView.from(requireFund(id));
    }

    /** 更新基金;仅合并请求中非 null 的字段(含类型字段,用户可覆盖自动识别结果)。 */
    @Transactional
    public FundView update(Long id, FundCreateRequest request) {
        FundEntity fund = requireFund(id);
        if (request.fundName() != null) {
            fund.setFundName(request.fundName());
        }
        if (request.fundCategory() != null) {
            fund.setFundCategory(request.fundCategory());
        }
        if (request.fundSubType() != null) {
            fund.setFundSubType(request.fundSubType());
        }
        if (request.benchmarkIndexCode() != null) {
            fund.setBenchmarkIndexCode(request.benchmarkIndexCode());
        }
        if (request.plannedTotalAmount() != null) {
            fund.setPlannedTotalAmount(request.plannedTotalAmount());
            validatePlannedTotalAmount(fund.getPlannedTotalAmount(), fund.getFundCategory());
        }
        return FundView.from(fundRepository.save(fund));
    }

    /** 归档基金(级联软删),委托 {@link FundArchiveService}。 */
    @Transactional
    public void archive(Long id) {
        fundArchiveService.archive(id);
    }

    /**
     * 计划仓位校验(CONTEXT.md「计划仓位校验」):plannedTotalAmount ≤ 总可投资金 × 单品种仓位上限,
     * 防止填一个根本建不了的死状态;与硬约束互补(意图上限 vs 事实上限)。
     * plannedTotalAmount 为 null 不校验;fundCategory 为 null 抛 FUND_CATEGORY_REQUIRED。
     */
    private void validatePlannedTotalAmount(BigDecimal plannedTotalAmount, FundCategory fundCategory) {
        if (plannedTotalAmount == null) {
            return;
        }
        if (fundCategory == null) {
            throw new BusinessException(ErrorCode.FUND_CATEGORY_REQUIRED, "计划仓位校验需要基金类型");
        }
        BigDecimal limit = userConfigService.requireTotalInvestableCapital()
                .multiply(HardConstraintConfig.singlePositionLimit(fundCategory));
        if (plannedTotalAmount.compareTo(limit) > 0) {
            throw new BusinessException(ErrorCode.PLANNED_AMOUNT_EXCEEDS_LIMIT,
                    "计划总仓位超过单品种仓位上限 " + limit);
        }
    }

    private FundEntity requireFund(Long id) {
        return fundRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + id + " 不存在"));
    }
}
