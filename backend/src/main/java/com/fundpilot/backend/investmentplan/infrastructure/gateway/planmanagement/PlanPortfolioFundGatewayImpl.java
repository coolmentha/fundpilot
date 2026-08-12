package com.fundpilot.backend.investmentplan.infrastructure.gateway.planmanagement;

import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.portfolio.adapter.api.fundtracking.PortfolioFundApi;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanPortfolioFundGatewayImpl implements PlanPortfolioFundGateway {
    private final PortfolioFundApi portfolioFunds;
    private final FundProductApi products;

    @Override
    public PortfolioFund requireTrackedByLegacyFund(long ownerId, long legacyFundId) {
        var fund = portfolioFunds.findOwnedByLegacyFundId(ownerId, legacyFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "组合基金不存在"));
        return requireTracked(fund);
    }

    @Override
    public PortfolioFund requireTracked(long ownerId, long portfolioFundId) {
        var fund = portfolioFunds.findOwned(ownerId, portfolioFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "组合基金不存在"));
        return requireTracked(fund);
    }

    @Override
    public List<PortfolioFund> findTrackedByOwner(long ownerId) {
        var tracked = portfolioFunds.findByOwner(ownerId).stream()
                .filter(fund -> fund.validity() == PortfolioFundApi.Validity.TRACKED).toList();
        Map<Long, FundProductApi.Product> productsById = products.findByIds(tracked.stream()
                .map(PortfolioFundApi.PortfolioFund::fundProductId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(FundProductApi.Product::id, Function.identity()));
        return tracked.stream().map(fund -> toPortfolioFund(fund, productsById.get(fund.fundProductId()))).toList();
    }

    @Override
    public Optional<PortfolioFund> findTrackedForExecution(long ownerId, long portfolioFundId) {
        return portfolioFunds.findForUpdate(portfolioFundId)
                .filter(fund -> fund.ownerId() == ownerId)
                .filter(fund -> fund.validity() == PortfolioFundApi.Validity.TRACKED)
                .map(fund -> toPortfolioFund(fund, product(fund.fundProductId())));
    }

    private PortfolioFund requireTracked(PortfolioFundApi.PortfolioFund fund) {
        if (fund.validity() != PortfolioFundApi.Validity.TRACKED) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE_TRANSITION, "作废组合基金不能管理定投计划");
        }
        return toPortfolioFund(fund, product(fund.fundProductId()));
    }

    private FundProductApi.Product product(long productId) {
        return products.findById(productId).orElse(null);
    }

    private static PortfolioFund toPortfolioFund(PortfolioFundApi.PortfolioFund fund,
                                                  FundProductApi.Product product) {
        if (product == null) return new PortfolioFund(fund.id(), fund.legacyFundId());
        return new PortfolioFund(fund.id(), fund.legacyFundId(), fund.fundProductId(),
                product.benchmarkIndexCode());
    }
}
