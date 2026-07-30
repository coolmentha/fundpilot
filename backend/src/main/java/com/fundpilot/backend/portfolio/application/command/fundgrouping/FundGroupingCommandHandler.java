package com.fundpilot.backend.portfolio.application.command.fundgrouping;

import com.fundpilot.backend.portfolio.domain.fundgroup.FundGroup;
import com.fundpilot.backend.portfolio.domain.fundgroup.FundGroupRepository;
import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundRepository;
import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundValidity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FundGroupingCommandHandler {
    private final FundGroupRepository groups;
    private final PortfolioFundRepository portfolioFunds;

    @Transactional
    public List<GroupResult> replace(long ownerId, List<GroupInput> inputs) {
        if (inputs == null) {
            throw new FundGroupingFailure(FundGroupingFailure.Code.FUND_GROUP_NAME_INVALID,
                    "分组列表不能为空");
        }
        Set<Long> submittedIds = new HashSet<>();
        Set<String> submittedNames = new HashSet<>();
        List<FundGroup> requested = new java.util.ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            GroupInput input = inputs.get(index);
            String name = normalizeName(input == null ? null : input.name());
            if (!submittedNames.add(name.toLowerCase(Locale.ROOT))) {
                throw new FundGroupingFailure(FundGroupingFailure.Code.FUND_GROUP_NAME_DUPLICATE,
                        "分组名称不能重复");
            }
            Long id = input == null ? null : input.id();
            if (id != null && !submittedIds.add(id)) {
                throw new FundGroupingFailure(FundGroupingFailure.Code.FUND_GROUP_NOT_FOUND,
                        "分组不存在或重复提交: " + id);
            }
            requested.add(new FundGroup(id, ownerId, name, index));
        }
        Set<Long> existingIds = groups.findByOwnerId(ownerId).stream()
                .map(FundGroup::id).collect(java.util.stream.Collectors.toSet());
        if (!existingIds.containsAll(submittedIds)) {
            throw new FundGroupingFailure(FundGroupingFailure.Code.FUND_GROUP_NOT_FOUND,
                    "提交了不属于当前用户的分组");
        }
        return groups.replace(ownerId, requested).stream().map(GroupResult::from).toList();
    }

    @Transactional
    public void assignByNames(long ownerId, long portfolioFundId, List<String> requestedNames) {
        if (requestedNames == null) {
            return;
        }
        var portfolioFund = portfolioFunds.findById(portfolioFundId)
                .filter(item -> item.ownerId() == ownerId
                        && item.validity() == PortfolioFundValidity.TRACKED)
                .orElseThrow(() -> new FundGroupingFailure(
                        FundGroupingFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                        "组合基金不存在: " + portfolioFundId));
        Set<String> unique = new HashSet<>();
        List<String> names = requestedNames.stream().map(this::normalizeName).toList();
        if (names.stream().map(name -> name.toLowerCase(Locale.ROOT)).anyMatch(name -> !unique.add(name))) {
            throw new FundGroupingFailure(FundGroupingFailure.Code.FUND_GROUP_NAME_DUPLICATE,
                    "分组名称不能重复");
        }
        groups.assignByNames(ownerId, portfolioFundId, portfolioFund.legacyFundId(), names);
    }

    private String normalizeName(String name) {
        try {
            return FundGroup.normalizeName(name);
        } catch (IllegalArgumentException failure) {
            throw new FundGroupingFailure(FundGroupingFailure.Code.FUND_GROUP_NAME_INVALID,
                    failure.getMessage());
        }
    }

    public record GroupInput(Long id, String name) {
    }

    public record GroupResult(long id, String name, int sortOrder) {
        static GroupResult from(FundGroup group) {
            return new GroupResult(group.id(), group.name(), group.sortOrder());
        }
    }

}
