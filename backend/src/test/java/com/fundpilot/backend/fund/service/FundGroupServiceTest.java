package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundGroupSaveRequest;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class FundGroupServiceTest extends AbstractIntegrationTest {
    @Autowired FundGroupService fundGroupService;
    @Autowired EntityManager entityManager;

    @Test
    void save_新增并保存排序() {
        var saved = fundGroupService.save(request(null, "核心", null, "观察"));

        assertThat(saved).extracting("name").containsExactly("核心", "观察");
        var reordered = fundGroupService.save(request(saved.get(1).id(), "观察", saved.get(0).id(), "核心"));
        assertThat(reordered).extracting("name").containsExactly("观察", "核心");
    }

    @Test
    void save_大小写不同的重复名称_拒绝() {
        assertThatThrownBy(() -> fundGroupService.save(request(null, "QDII", null, "qdii")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.FUND_GROUP_NAME_DUPLICATE.name());
    }

    @Test
    void save_缺少分组列表_拒绝而不是删除全部() {
        fundGroupService.save(request(null, "核心"));

        assertThatThrownBy(() -> fundGroupService.save(new FundGroupSaveRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.FUND_GROUP_NAME_INVALID.name());
        assertThat(fundGroupService.list()).extracting("name").containsExactly("核心");
    }

    @Test
    void save_删除分组只解除关联_基金仍保留() {
        var group = fundGroupService.save(request(null, "核心")).get(0);
        FundEntity fund = new FundEntity();
        fund.setOwnerId(testActorId());
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setGroups(new LinkedHashSet<>(fundGroupService.resolveNames(List.of("核心"))));
        entityManager.persist(fund);
        entityManager.flush();

        fundGroupService.save(new FundGroupSaveRequest(List.of()));
        entityManager.flush();
        entityManager.clear();

        FundEntity reloaded = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getGroups()).isEmpty();
        assertThat(fundGroupService.list()).noneMatch(item -> item.id().equals(group.id()));
    }

    private static FundGroupSaveRequest request(Object... values) {
        java.util.ArrayList<FundGroupSaveRequest.Item> items = new java.util.ArrayList<>();
        for (int i = 0; i < values.length; i += 2) {
            items.add(new FundGroupSaveRequest.Item((Long) values[i], (String) values[i + 1]));
        }
        return new FundGroupSaveRequest(items);
    }
}
