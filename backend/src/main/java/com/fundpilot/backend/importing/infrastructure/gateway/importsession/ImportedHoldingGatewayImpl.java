package com.fundpilot.backend.importing.infrastructure.gateway.importsession;

import com.fundpilot.backend.accounting.adapter.api.fundonboarding.PortfolioFundOnboardingApi;
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
    private final PlatformTransactionManager transactionManager;
    private final ImportItemReceiptStore receipts;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ItemResult importItem(ItemRequest request) {
        var completed = receipts.find(request);
        if (completed.isPresent()) return completed.get();
        var candidates = find(request.ownerId(), request.fundCode()).isEmpty()
                ? navPrefetch.fetch(request.fundCode()) : List.<PublishedNavApi.NavCandidate>of();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(30);
        return transaction.execute(status -> {
            receipts.lock(request);
            var previous = receipts.find(request);
            if (previous.isPresent()) return previous.get();
            var existing = find(request.ownerId(), request.fundCode());
            ItemResult result;
            if (existing.isEmpty()) {
                var created = createLocal(request.ownerId(), request.fundCode(), request.fundName(),
                        request.shares(), request.costPerShare(), request.groupNames(), candidates);
                result = new ItemResult(ItemStatus.CREATED, "已新增基金", created.portfolioFundId());
            } else if (request.mode() == null) {
                throw new YangjibaoImportFailure(YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_INVALID,
                        "请选择已有基金的处理方式");
            } else if (request.mode() == ExistingMode.KEEP_LOCAL) {
                result = new ItemResult(ItemStatus.SKIPPED, "以本系统份额为准", existing.get().portfolioFundId());
            } else {
                synchronize(request.ownerId(), existing.get().portfolioFundId(), request.shares());
                result = new ItemResult(ItemStatus.ADJUSTED, "已按目标份额调整", existing.get().portfolioFundId());
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

    @Override
    public ImportedHolding create(long ownerId, String fundCode, String fundName, BigDecimal shares,
                                  BigDecimal costPerShare, List<String> groupNames) {
        var candidates = navPrefetch.fetch(fundCode);
        return new TransactionTemplate(transactionManager).execute(status ->
                createLocal(ownerId, fundCode, fundName, shares, costPerShare, groupNames, candidates));
    }

    private ImportedHolding createLocal(long ownerId, String fundCode, String fundName, BigDecimal shares,
                                        BigDecimal costPerShare, List<String> groupNames,
                                        List<PublishedNavApi.NavCandidate> candidates) {
        var product = products.ensure(new FundProductApi.EnsureProduct(fundCode, fundName, fundName, null));
        publishedNavs.publishNewer(new PublishedNavApi.PublishNavs(null, product.id(), fundCode, candidates));
        var result = onboarding.onboard(new PortfolioFundOnboardingApi.OnboardPortfolioFund(null, ownerId,
                product.id(), true, DEFAULT_WARNING_RATIO, shares, costPerShare, null));
        groups.assignByNames(new PortfolioGroupingApi.AssignByNames(ownerId, result.portfolioFundId(), groupNames));
        return portfolioFunds.findOwned(ownerId, result.portfolioFundId())
                .map(fund -> new ImportedHolding(fund.id(), fund.legacyFundId()))
                .orElseThrow();
    }

    @Override
    public boolean synchronize(long ownerId, long portfolioFundId, BigDecimal targetShares) {
        return transactions.adjustToHoldingShares(new TransactionApi.AdjustToHoldingShares(
                ownerId, portfolioFundId, targetShares)).transaction() != null;
    }
}
