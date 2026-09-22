package com.fundpilot.backend.importing.infrastructure.gateway.importsession;

import com.fundpilot.backend.accounting.adapter.api.fundonboarding.PortfolioFundOnboardingApi;
import com.fundpilot.backend.accounting.adapter.api.portfoliocorrection.PortfolioCostCorrectionApi;
import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.accounting.adapter.api.transaction.TransactionApi;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.importing.application.command.importsession.YangjibaoImportFailure;
import com.fundpilot.backend.importing.infrastructure.persistence.importitem.ImportItemReceiptStore;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.NavPrefetchApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import com.fundpilot.backend.portfolio.adapter.api.fundgrouping.PortfolioGroupingApi;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class ImportedHoldingGatewayImpl implements ImportedHoldingGateway {
    private static final BigDecimal DEFAULT_WARNING_RATIO = new BigDecimal("0.30");
    private final FundProductApi products;
    private final NavPrefetchApi navPrefetch;
    private final PublishedNavApi publishedNavs;
    private final PortfolioFundApi portfolioFunds;
    private final PortfolioGroupingApi groups;
    private final PortfolioFundOnboardingApi onboarding;
    private final PositionApi positions;
    private final TransactionApi transactions;
    private final PortfolioCostCorrectionApi corrections;
    private final PlatformTransactionManager transactionManager;
    private final ImportItemReceiptStore receipts;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ItemResult importItem(ItemRequest request) {
        var completed = receipts.find(request);
        if (completed.isPresent()) return completed.get();
        List<PublishedNavApi.NavCandidate> candidates;
        try {
            candidates = find(request.ownerId(), request.fundCode()).isEmpty()
                    ? navPrefetch.fetch(request.fundCode()) : List.of();
        } catch (RuntimeException failure) {
            throw new YangjibaoImportFailure(YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_DEPENDENCY_FAILED,
                    "行情依赖暂时不可用", failure);
        }
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(30);
        return transaction.execute(status -> {
            receipts.lock(request);
            var previous = receipts.find(request);
            if (previous.isPresent()) return previous.get();
            var existing = find(request.ownerId(), request.fundCode());
            ItemResult result;
            if (existing.isEmpty()) {
                long portfolioFundId;
                try {
                    portfolioFundId = createLocal(request.ownerId(), request.fundCode(), request.fundName(),
                            request.shares(), request.costPerShare(), request.groupNames(), candidates);
                } catch (PortfolioFundOnboardingApi.Failure failure) {
                    throw classify(failure);
                } catch (PortfolioGroupingApi.Failure failure) {
                    throw new YangjibaoImportFailure(
                            YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_VALIDATION_FAILED,
                            "导入分组无效", failure);
                }
                result = new ItemResult(ItemStatus.CREATED, "已新增基金", portfolioFundId);
            } else if (request.mode() == null) {
                throw new YangjibaoImportFailure(YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_CONFLICT,
                        "请选择已有基金的处理方式");
            } else if (request.mode() == ExistingMode.KEEP_LOCAL) {
                result = new ItemResult(ItemStatus.SKIPPED, "以本系统份额为准", existing.get().portfolioFundId());
            } else {
                long portfolioFundId = existing.get().portfolioFundId();
                transactions.adjustToHoldingShares(new TransactionApi.AdjustToHoldingShares(
                        request.ownerId(), portfolioFundId, request.shares()));
                syncCostPerShare(request, portfolioFundId);
                result = new ItemResult(ItemStatus.ADJUSTED, "已按目标份额调整", portfolioFundId);
            }
            receipts.save(request, result);
            return result;
        });
    }

    @Override
    public Optional<LocalHolding> find(long ownerId, String fundCode) {
        return products.findByCode(fundCode).flatMap(product -> portfolioFunds.findByOwner(ownerId).stream()
                .filter(fund -> fund.fundProductId() == product.id()
                        && fund.validity() == PortfolioFundApi.Validity.TRACKED)
                .findFirst().map(fund -> new LocalHolding(fund.id(), fund.legacyFundId(),
                        positions.findOwned(ownerId, fund.id()).map(PositionApi.Position::confirmedShares)
                                .orElse(BigDecimal.ZERO))));
    }

    private long createLocal(long ownerId, String fundCode, String fundName, BigDecimal shares,
                             BigDecimal costPerShare, List<String> groupNames,
                             List<PublishedNavApi.NavCandidate> candidates) {
        var product = products.ensure(new FundProductApi.EnsureProduct(fundCode, fundName, fundName, null));
        publishedNavs.publishNewer(new PublishedNavApi.PublishNavs(null, product.id(), fundCode, candidates));
        var result = onboarding.onboard(new PortfolioFundOnboardingApi.OnboardPortfolioFund(null, ownerId,
                product.id(), true, DEFAULT_WARNING_RATIO, shares, costPerShare, null));
        groups.assignByNames(new PortfolioGroupingApi.AssignByNames(ownerId, result.portfolioFundId(), groupNames));
        return result.portfolioFundId();
    }

    /**
     * 以平台为准同步份额时,把平台成本单价一并写成本地成本基准,否则补进来的份额是零成本,
     * 持仓成本会低于平台真实投入。平台未提供成本单价或目标份额为 0(清仓)时保持账本原样;
     * 与本地成本一致时不写成本重置流水。
     */
    private void syncCostPerShare(ItemRequest request, long portfolioFundId) {
        BigDecimal costPerShare = request.costPerShare();
        if (costPerShare == null || costPerShare.signum() <= 0
                || request.shares() == null || request.shares().signum() <= 0) {
            return;
        }
        BigDecimal local = positions.findOwned(request.ownerId(), portfolioFundId)
                .map(PositionApi.Position::costPerShare).orElse(null);
        if (local != null && local.compareTo(costPerShare) == 0) return;
        try {
            corrections.correct(new PortfolioCostCorrectionApi.CorrectCostPerShare(
                    request.ownerId(), portfolioFundId, costPerShare));
        } catch (PortfolioCostCorrectionApi.Failure failure) {
            throw classify(failure);
        }
    }

    private YangjibaoImportFailure classify(PortfolioCostCorrectionApi.Failure failure) {
        return switch (failure.code()) {
            case COST_PER_SHARE_INVALID -> new YangjibaoImportFailure(
                    YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_VALIDATION_FAILED, "导入成本单价无效", failure);
            case PORTFOLIO_FUND_NOT_FOUND, PORTFOLIO_FUND_NOT_OPEN -> new YangjibaoImportFailure(
                    YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_CONFLICT, "本地持仓状态与平台不一致", failure);
        };
    }

    private YangjibaoImportFailure classify(PortfolioFundOnboardingApi.Failure failure) {
        var code = switch (failure.code()) {
            case POSITION_WARNING_INVALID, INITIAL_HOLDING_SHARES_INVALID, COST_PER_SHARE_INVALID,
                    OPENED_AT_IN_FUTURE, FUND_GROUP_NAME_INVALID, FUND_GROUP_NAME_DUPLICATE ->
                    YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_VALIDATION_FAILED;
            case PORTFOLIO_FUND_ALREADY_TRACKED -> YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_CONFLICT;
            case PRODUCT_NOT_FOUND, NAV_UNAVAILABLE ->
                    YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_DEPENDENCY_FAILED;
            default -> null;
        };
        if (code == null) throw failure;
        return new YangjibaoImportFailure(code, "导入条目处理失败", failure);
    }
}
