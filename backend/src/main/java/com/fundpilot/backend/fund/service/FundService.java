package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.accounting.adapter.api.portfoliocorrection.PortfolioCostCorrectionApi;
import com.fundpilot.backend.accounting.adapter.api.fundonboarding.PortfolioFundOnboardingApi;
import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.discipline.adapter.api.classification.DisciplineClassificationApi;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.controller.FundView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.InvestmentTarget;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.support.FundTypeClassification;
import com.fundpilot.backend.fund.service.support.FundTypeClassifier;
import com.fundpilot.backend.marketdata.adapter.api.indicatorrefresh.MarketIndicatorRefreshApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.portfolio.adapter.api.fundgrouping.PortfolioGroupingApi;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基金服务(issue #16 + ADR-0005):基金 CRUD 业务逻辑,Controller 只做 HTTP 路由,逻辑下沉到本层。
 * <p>新建时类型字段(fundSubType/fundCategory/benchmarkIndexCode)优先用前端从字典搜索带入的值;
 * 缺省时后端按 fundName 兜底跑 {@link FundTypeClassifier} 识别(尽力填+可覆盖,工作台领域上下文「基金类型自动识别」)。
 * 产品身份由 ProductCatalog 统一登记，legacy 字段仅在后续切片完成前双写。
 * 返回 {@link FundView} DTO,不直接暴露 {@link FundEntity}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundService {

    private final FundRepository fundRepository;
    private final FundPnlService fundPnlService;
    private final MarketIndicatorRefreshApi marketDataRefresh;
    private final CurrentActorApi currentActorApi;
    private final FundProductApi productCatalogApi;
    private final PortfolioFundApi portfolioFundApi;
    private final PositionApi positionApi;
    private final PortfolioGroupingApi portfolioGroupingApi;
    private final PortfolioFundOnboardingApi onboardingApi;
    private final PortfolioCostCorrectionApi costCorrectionApi;
    private final DisciplineClassificationApi disciplineClassifications;

    private static final MathContext MATH = MathContext.DECIMAL64;

    /** 查全部基金(含今日涨跌/持仓盈亏,issue #18)。 */
    @Transactional(readOnly = true)
    public List<FundView> list() {
        long userId = currentActorApi.userId();
        Map<Long, PortfolioFundApi.PortfolioFund> portfolioByLegacyFundId = portfolioFundApi
                .findByOwner(userId).stream()
                .filter(portfolioFund -> portfolioFund.legacyFundId() != null)
                .collect(Collectors.toMap(PortfolioFundApi.PortfolioFund::legacyFundId,
                        Function.identity()));
        List<FundEntity> funds = fundRepository.findAllByOwnerId(userId).stream()
                .filter(fund -> {
                    var portfolioFund = portfolioByLegacyFundId.get(fund.getId());
                    return portfolioFund != null
                            && portfolioFund.validity() == PortfolioFundApi.Validity.TRACKED;
                })
                .toList();
        Map<Long, BigDecimal> currentCostByFundId = currentCostByFundId(userId, portfolioByLegacyFundId);
        var pnlByFund = fundPnlService.computeForFunds(funds, currentCostByFundId);
        return funds.stream()
                .map(fund -> FundView.from(fund, pnlByFund.get(fund.getId()),
                        portfolioByLegacyFundId.get(fund.getId()).id(),
                        currentCostOrLegacy(fund, currentCostByFundId)))
                .toList();
    }

    /**
     * 新建基金;类型字段优先用请求带入值,缺省时按 fundName 兜底识别。
     * <p>fundCode/fundName 二选一即可(工作台领域上下文「基金字典搜索」);两者都缺 → 业务异常。
     * <p><b>初始持仓录入(ADR-0012)</b>:initialHoldingShares 有值时走建仓路径——FundStatus→HOLDING、
     * openedAt=now、写一条 INCREASE 交易并用最近一期净值同步确认,
     * 对齐建议回应的状态流转,但确认时机尊重已有持仓盘点语义
     * (用已公布净值核算金额,不等 NavConfirmJob)。无净值可核算则报错不让建(同步确认的硬前提)。
     * <p>initialHoldingShares 为 null → 走原 PENDING_HOLDING 流程；非正数拒绝。
     * <p>@Transactional:initialHoldingShares 路径需写基金+交易原子。同步建仓必须取得净值，
     * 行情拉取失败时整个创建事务失败，避免返回一个没有可核算份额的半成品基金。
     */
    @Transactional
    public FundView create(FundCreateRequest request) {
        if (request.fundCode() == null || request.fundCode().isBlank()
                || request.fundName() == null || request.fundName().isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_FUND_IDENTITY, "基金代码和名称不能为空");
        }
        BigDecimal initialHoldingShares = ShareScale.normalize(request.initialHoldingShares());
        if (initialHoldingShares != null && initialHoldingShares.signum() <= 0) {
            throw new BusinessException(ErrorCode.INITIAL_HOLDING_SHARES_INVALID, "初始持仓份额必须大于 0");
        }
        FundEntity fund = new FundEntity();
        long userId = currentActorApi.userId();
        fund.setOwnerId(userId);
        fund.setFundCode(request.fundCode());
        fund.setFundName(request.fundName());
        fund.setInvestmentTarget(inferInvestmentTarget(request.fundName()));
        fund.setProductId(ensureProduct(fund).id());

        // 类型字段:请求带入优先,缺省时按 fundName 兜底识别(尽力填);各字段独立兜底(issue #149)
        FundTypeClassification fallback = FundTypeClassifier.classify(request.fundName());
        fund.setFundSubType(request.fundSubType() != null ? request.fundSubType() : fallback.fundSubType());
        fund.setFundCategory(request.fundCategory() != null ? request.fundCategory() : fallback.fundCategory());
        fund.setBenchmarkIndexCode(request.benchmarkIndexCode() != null ? request.benchmarkIndexCode()
                : fallback.benchmarkIndexCode());
        if (fund.getBenchmarkIndexCode() != null) {
            productCatalogApi.updateBenchmark(fund.getProductId(), fund.getBenchmarkIndexCode());
        }
        fund.setPositionWarningEnabled(request.positionWarningEnabled() == null || request.positionWarningEnabled());
        fund.setPositionWarningRatio(normalizePositionWarningRatio(request.positionWarningRatio()));
        validateFundCategory(fund.getFundCategory());
        FundEntity saved = fundRepository.save(fund);
        // 初始持仓先补齐行情；Accounting 在同一本地事务内创建 PortfolioFund、账目、lot 与 Position。
        if (initialHoldingShares != null) {
            marketDataRefresh.refreshOne(new MarketIndicatorRefreshApi.RefreshTarget(saved.getId(),
                    Objects.requireNonNull(saved.getProductId()), saved.getFundCode(), saved.getFundName(),
                    saved.getBenchmarkIndexCode(), saved.getInvestmentTarget() == null ? null
                    : MarketIndicatorRefreshApi.InvestmentTarget.valueOf(saved.getInvestmentTarget().name())));
        }
        PortfolioFundOnboardingApi.OnboardingResult onboarding;
        try {
            onboarding = onboardingApi.onboard(new PortfolioFundOnboardingApi.OnboardPortfolioFund(
                    saved.getId(), userId, Objects.requireNonNull(saved.getProductId()),
                    saved.isPositionWarningEnabled(), saved.getPositionWarningRatio(), initialHoldingShares,
                    request.costPerShare(), request.openedAt()));
        } catch (PortfolioFundOnboardingApi.Failure failure) {
            throw onboardingFailure(failure);
        }
        portfolioGroupingApi.assignByNames(new PortfolioGroupingApi.AssignByNames(
                userId, onboarding.portfolioFundId(), request.groupNames()));
        disciplineClassifications.set(new DisciplineClassificationApi.SetClassification(
                userId, onboarding.portfolioFundId(),
                DisciplineClassificationApi.Category.valueOf(saved.getFundCategory().name()),
                request.fundCategory() == null
                        ? DisciplineClassificationApi.Source.DEFAULT_SUGGESTION
                        : DisciplineClassificationApi.Source.USER_CONFIRMED));
        try {
            marketDataRefresh.refreshOneForPortfolioFund(onboarding.portfolioFundId());
        } catch (RuntimeException exception) {
            log.warn("基金开户后刷新行情失败 portfolio_fund={}", onboarding.portfolioFundId(), exception);
        }
        var currentPosition = positionApi.findOwned(userId, onboarding.portfolioFundId());
        BigDecimal currentCostPerShare = currentPosition.isPresent()
                ? currentPosition.orElseThrow().costPerShare() : saved.getCostPerShare();
        return FundView.from(saved, onboarding.portfolioFundId(), currentCostPerShare);
    }

    /** 查单个基金(含今日涨跌/持仓盈亏,issue #18);不存在抛 400(业务问题,非路由不存在)。 */
    @Transactional(readOnly = true)
    public FundView get(Long id) {
        FundEntity fund = requireFund(id);
        var portfolioFund = requireTrackedPortfolioFund(fund);
        Map<Long, BigDecimal> currentCostByFundId = new HashMap<>();
        positionApi.findOwned(currentActorApi.userId(), portfolioFund.id())
                .ifPresent(position -> currentCostByFundId.put(fund.getId(), position.costPerShare()));
        return FundView.from(fund, fundPnlService.computeForFund(fund, currentCostByFundId),
                portfolioFund.id(), currentCostOrLegacy(fund, currentCostByFundId));
    }

    /** 更新基金;仅合并请求中非 null 的字段(含类型字段,用户可覆盖自动识别结果)。 */
    @Transactional
    public FundView update(Long id, FundCreateRequest request) {
        FundEntity fund = requireFund(id);
        requireTrackedPortfolioFund(fund);
        if (request.fundName() != null) {
            fund.setFundName(request.fundName());
            if (fund.getInvestmentTarget() == null) {
                fund.setInvestmentTarget(inferInvestmentTarget(request.fundName()));
            }
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
        if (request.positionWarningEnabled() != null) {
            fund.setPositionWarningEnabled(request.positionWarningEnabled());
        }
        if (request.positionWarningRatio() != null) {
            fund.setPositionWarningRatio(normalizePositionWarningRatio(request.positionWarningRatio()));
        }
        fund.setProductId(ensureProduct(fund).id());
        if (request.benchmarkIndexCode() != null) {
            productCatalogApi.updateBenchmark(fund.getProductId(), request.benchmarkIndexCode());
        }
        FundEntity saved = fundRepository.save(fund);
        var portfolioFund = requirePortfolioFund(saved);
        portfolioFund = portfolioFundApi.configureWarning(new PortfolioFundApi.ConfigurePositionWarning(
                saved.getOwnerId(), portfolioFund.id(), saved.isPositionWarningEnabled(),
                saved.getPositionWarningRatio()));
        portfolioGroupingApi.assignByNames(new PortfolioGroupingApi.AssignByNames(
                saved.getOwnerId(), portfolioFund.id(), request.groupNames()));
        var currentPosition = positionApi.findOwned(saved.getOwnerId(), portfolioFund.id());
        BigDecimal responseCostPerShare = currentPosition.isPresent()
                ? currentPosition.orElseThrow().costPerShare() : saved.getCostPerShare();
        if (request.costPerShare() != null) {
            try {
                responseCostPerShare = costCorrectionApi.correct(new PortfolioCostCorrectionApi.CorrectCostPerShare(
                        saved.getOwnerId(), portfolioFund.id(), request.costPerShare()))
                        .costPerShare();
            } catch (PortfolioCostCorrectionApi.Failure failure) {
                throw costCorrectionFailure(failure);
            }
        }
        if (request.fundCategory() != null) {
            disciplineClassifications.set(new DisciplineClassificationApi.SetClassification(
                    saved.getOwnerId(), portfolioFund.id(),
                    DisciplineClassificationApi.Category.valueOf(saved.getFundCategory().name()),
                    DisciplineClassificationApi.Source.USER_CUSTOMIZED));
        }
        return FundView.from(saved, portfolioFund.id(), responseCostPerShare);
    }

    /**
     * fundCategory 校验:类型为 null 会阻塞后续默认档位查询(宽基/行业/主动/混合 各有默认档位表),
     * 故建仓/编辑时强制非 null。plannedTotalAmount 上限校验已随总可投资金移除(V9)。
     */
    private void validateFundCategory(FundCategory fundCategory) {
        if (fundCategory == null) {
            throw new BusinessException(ErrorCode.FUND_CATEGORY_REQUIRED, "基金类型不能为空(阻塞默认档位查询)");
        }
    }

    private InvestmentTarget inferInvestmentTarget(String fundName) {
        return fundName != null && fundName.toUpperCase(java.util.Locale.ROOT).contains("QDII")
                ? InvestmentTarget.QDII : null;
    }

    private FundProductApi.ProductReference ensureProduct(FundEntity fund) {
        FundProductApi.InvestmentTarget target = fund.getInvestmentTarget() == null ? null
                : FundProductApi.InvestmentTarget.valueOf(fund.getInvestmentTarget().name());
        return productCatalogApi.ensure(new FundProductApi.EnsureProduct(
                fund.getFundCode(), fund.getFundName(), null, target));
    }

    private BigDecimal normalizePositionWarningRatio(BigDecimal ratio) {
        BigDecimal value = ratio != null ? ratio : FundEntity.DEFAULT_POSITION_WARNING_RATIO;
        if (value.signum() <= 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(ErrorCode.POSITION_WARNING_RATIO_INVALID,
                    "仓位提醒线必须大于 0 且不超过 100%");
        }
        return value;
    }

    /** legacy Web 入口暂保留既有错误码；新开户事实已由 Accounting 持有。 */
    private BusinessException onboardingFailure(PortfolioFundOnboardingApi.Failure failure) {
        ErrorCode code = switch (failure.code()) {
            case INITIAL_HOLDING_SHARES_INVALID -> ErrorCode.INITIAL_HOLDING_SHARES_INVALID;
            case COST_PER_SHARE_INVALID -> ErrorCode.COST_PER_SHARE_INVALID;
            case OPENED_AT_IN_FUTURE -> ErrorCode.OPENED_AT_IN_FUTURE;
            case NAV_UNAVAILABLE -> ErrorCode.NAV_HISTORY_EMPTY;
            case PRODUCT_NOT_FOUND, PORTFOLIO_FUND_ALREADY_TRACKED, POSITION_WARNING_INVALID ->
                    ErrorCode.FUND_NOT_FOUND;
        };
        return new BusinessException(code, failure.getMessage());
    }

    private BusinessException costCorrectionFailure(PortfolioCostCorrectionApi.Failure failure) {
        ErrorCode code = switch (failure.code()) {
            case COST_PER_SHARE_INVALID -> ErrorCode.COST_PER_SHARE_INVALID;
            case PORTFOLIO_FUND_NOT_FOUND, PORTFOLIO_FUND_NOT_OPEN -> ErrorCode.FUND_NOT_FOUND;
        };
        return new BusinessException(code, failure.getMessage());
    }

    private PortfolioFundApi.PortfolioFund requirePortfolioFund(FundEntity fund) {
        return portfolioFundApi.findOwnedByLegacyFundId(fund.getOwnerId(), fund.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "legacy fund 缺少 PortfolioFund 映射: " + fund.getId()));
    }

    private PortfolioFundApi.PortfolioFund requireTrackedPortfolioFund(FundEntity fund) {
        var portfolioFund = requirePortfolioFund(fund);
        if (portfolioFund.validity() != PortfolioFundApi.Validity.TRACKED) {
            throw new BusinessException(ErrorCode.FUND_NOT_FOUND,
                    "Fund #" + fund.getId() + " 不存在");
        }
        return portfolioFund;
    }

    private FundEntity requireFund(Long id) {
        long userId = currentActorApi.userId();
        return fundRepository.findByIdAndOwnerId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + id + " 不存在"));
    }

    private Map<Long, BigDecimal> currentCostByFundId(long ownerId,
                                                       Map<Long, PortfolioFundApi.PortfolioFund> portfolioByLegacyFundId) {
        Map<Long, BigDecimal> costByPortfolioFundId = new HashMap<>();
        positionApi.findByOwner(ownerId).forEach(position ->
                costByPortfolioFundId.put(position.portfolioFundId(), position.costPerShare()));
        Map<Long, BigDecimal> result = new HashMap<>();
        portfolioByLegacyFundId.values().forEach(portfolioFund -> {
            if (costByPortfolioFundId.containsKey(portfolioFund.id())) {
                result.put(portfolioFund.legacyFundId(), costByPortfolioFundId.get(portfolioFund.id()));
            }
        });
        return result;
    }

    private BigDecimal currentCostOrLegacy(FundEntity fund, Map<Long, BigDecimal> currentCostByFundId) {
        return currentCostByFundId.containsKey(fund.getId())
                ? currentCostByFundId.get(fund.getId()) : fund.getCostPerShare();
    }
}
