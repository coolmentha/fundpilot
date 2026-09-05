package com.fundpilot.backend.portfolio.application.query.fundtracking;

import com.fundpilot.backend.portfolio.application.gateway.fundtracking.FundProductGateway;
import com.fundpilot.backend.portfolio.domain.fundgroup.FundGroupRepository;
import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFund;
import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 组合基金 HTTP 视图查询，组合、产品和分组事实在此处组装。 */
@Service
@RequiredArgsConstructor
public class PortfolioFundViewQueryHandler {
    private final PortfolioFundRepository portfolioFunds;
    private final FundGroupRepository groups;
    private final FundProductGateway products;

    @Transactional(readOnly = true)
    public List<ViewResult> findByOwner(long ownerId) {
        List<PortfolioFund> funds = portfolioFunds.findByOwnerId(ownerId);
        return views(ownerId, funds);
    }

    @Transactional(readOnly = true)
    public Optional<ViewResult> findOwned(long ownerId, long portfolioFundId) {
        return portfolioFunds.findById(portfolioFundId)
                .filter(fund -> fund.ownerId() == ownerId)
                .map(fund -> views(ownerId, List.of(fund)).getFirst());
    }

    private List<ViewResult> views(long ownerId, List<PortfolioFund> funds) {
        Set<Long> productIds = funds.stream().map(PortfolioFund::fundProductId).collect(Collectors.toSet());
        Map<Long, FundProductGateway.Product> productById = products.findByIds(productIds).stream()
                .collect(Collectors.toMap(FundProductGateway.Product::id, Function.identity()));
        Map<Long, List<GroupResult>> groupsByFund = groups.memberships(ownerId).stream()
                .collect(Collectors.groupingBy(FundGroupRepository.GroupMembership::portfolioFundId,
                        Collectors.mapping(value -> new GroupResult(value.groupId(), value.groupName()),
                                Collectors.toList())));
        return funds.stream().map(fund -> view(fund, productById, groupsByFund)).toList();
    }

    private ViewResult view(PortfolioFund fund, Map<Long, FundProductGateway.Product> productById,
                            Map<Long, List<GroupResult>> groupsByFund) {
        FundProductGateway.Product product = Optional.ofNullable(productById.get(fund.fundProductId()))
                .orElseThrow(() -> new IllegalStateException("组合基金缺少产品目录记录: " + fund.fundProductId()));
        return new ViewResult(fund.id(), fund.fundProductId(), product.fundCode(), product.fundName(),
                product.productType(), product.investmentTarget(), product.benchmarkIndexCode(),
                fund.validity().name(), fund.positionWarningEnabled(), fund.positionWarningRatio(),
                groupsByFund.getOrDefault(fund.id(), List.of()));
    }

    public record ViewResult(long portfolioFundId, long fundProductId, String fundCode, String fundName,
                             String productType, String investmentTarget, String benchmarkIndexCode,
                             String validity, boolean positionWarningEnabled,
                             BigDecimal positionWarningRatio, List<GroupResult> groups) {
    }

    public record GroupResult(long id, String name) {
    }
}
