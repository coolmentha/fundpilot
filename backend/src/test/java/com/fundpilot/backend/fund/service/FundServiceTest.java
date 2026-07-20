package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.controller.FundView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 基金创建/更新校验。
 *
 * <p>金字塔加仓机制移除后,plannedTotalAmount 字段及其上限校验已删除——买入完全由用户手动/定投决定。
 * 保留的校验:fundCategory 非 null(阻塞默认档位查询)。
 */
@Transactional
class FundServiceTest extends AbstractIntegrationTest {

    @Autowired FundService fundService;
    @Autowired EntityManager entityManager;

    @Test
    void create_正常创建() {
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, FundSubType.ETF, null);

        FundView view = fundService.create(request);

        assertThat(view.fundCode()).isEqualTo("510300");
        assertThat(view.fundName()).isEqualTo("沪深300ETF");
        assertThat(view.fundCategory()).isEqualTo(FundCategory.BROAD_BASE);
        assertThat(view.positionWarningEnabled()).isTrue();
        assertThat(view.positionWarningRatio()).isEqualByComparingTo("0.30");
    }

    @Test
    void create_可同时加入多个分组() {
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, FundSubType.ETF, null,
                null, null, null, null, null, List.of("核心", "宽基"));

        FundView view = fundService.create(request);

        assertThat(view.groups()).extracting("name").containsExactly("核心", "宽基");
    }

    @Test
    void create_类型为空_抛异常() {
        // fundSubType 非 null 跳过兜底识别,使 fundCategory 保持 null
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", null, FundSubType.ETF, null);

        assertThatThrownBy(() -> fundService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.FUND_CATEGORY_REQUIRED.name());
    }

    @Test
    void create_初始持仓份额非正数_抛异常() {
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, FundSubType.ETF, null,
                BigDecimal.ZERO);

        assertThatThrownBy(() -> fundService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.INITIAL_HOLDING_SHARES_INVALID.name());
    }

    @Test
    void update_合并非null字段_保留未传字段() {
        FundEntity fund = persistFund(FundCategory.BROAD_BASE);
        entityManager.flush();
        FundCreateRequest request = new FundCreateRequest(
                null, "新名称", null, null, null);

        fundService.update(fund.getId(), request);

        entityManager.flush();
        entityManager.clear();
        FundEntity reloaded = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloaded.getFundName()).isEqualTo("新名称");
        assertThat(reloaded.getFundCategory()).isEqualTo(FundCategory.BROAD_BASE);
    }

    @Test
    void update_仓位提醒线超过100百分比_抛POSITION_WARNING_RATIO_INVALID() {
        FundEntity fund = persistFund(FundCategory.BROAD_BASE);
        entityManager.flush();
        FundCreateRequest request = new FundCreateRequest(
                null, null, null, null, null, null,
                new BigDecimal("1.01"), null, null, null);

        assertThatThrownBy(() -> fundService.update(fund.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.POSITION_WARNING_RATIO_INVALID.name());
    }

    private FundEntity persistFund(FundCategory category) {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(category);
        entityManager.persist(fund);
        return fund;
    }
}
