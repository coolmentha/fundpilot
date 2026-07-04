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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 基金创建/更新校验。
 *
 * <p>行情工作台转向后,计划仓位上限校验(plannedTotalAmount ≤ 总可投资金 × 30%)已随
 * totalInvestableCapital 移除。保留的校验:fundCategory 非 null(阻塞默认档位查询)。
 * plannedTotalAmount 仍可填,但不再卡上限——策略降级为辅助,仓位管理不再是核心。
 */
@Transactional
class FundServiceTest extends AbstractIntegrationTest {

    @Autowired FundService fundService;
    @Autowired EntityManager entityManager;

    @Test
    void create_正常创建_含计划仓位() {
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, null, null, new BigDecimal("30000"));

        FundView view = fundService.create(request);

        assertThat(view.plannedTotalAmount()).isEqualByComparingTo("30000");
    }

    @Test
    void create_不填计划仓位_正常创建() {
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, null, null, null);

        FundView view = fundService.create(request);

        assertThat(view.plannedTotalAmount()).isNull();
    }

    @Test
    void create_类型为空_抛异常() {
        // fundSubType 非 null 跳过兜底识别,使 fundCategory 保持 null
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", null, FundSubType.ETF, null, new BigDecimal("5000"));

        assertThatThrownBy(() -> fundService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.FUND_CATEGORY_REQUIRED.name());
    }

    @Test
    void create_计划仓位任意金额_不再校验上限() {
        // 原上限 = 总可投资金 × 30%,现已移除;填超大金额也能创建
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, null, null, new BigDecimal("999999999"));

        FundView view = fundService.create(request);

        assertThat(view.plannedTotalAmount()).isEqualByComparingTo("999999999");
    }

    @Test
    void update_计划仓位改到任意值_不再校验上限() {
        FundEntity fund = persistFund(FundCategory.BROAD_BASE, new BigDecimal("10000"));
        entityManager.flush();
        FundCreateRequest request = new FundCreateRequest(
                null, null, null, null, null, new BigDecimal("999999999"));

        fundService.update(fund.getId(), request);

        entityManager.flush();
        entityManager.clear();
        FundEntity reloaded = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloaded.getPlannedTotalAmount()).isEqualByComparingTo("999999999");
    }

    @Test
    void update_不传计划仓位_保留原值() {
        FundEntity fund = persistFund(FundCategory.BROAD_BASE, new BigDecimal("10000"));
        entityManager.flush();
        FundCreateRequest request = new FundCreateRequest(
                null, "新名称", null, null, null, null);

        fundService.update(fund.getId(), request);

        entityManager.flush();
        entityManager.clear();
        FundEntity reloaded = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloaded.getPlannedTotalAmount()).isEqualByComparingTo("10000");
        assertThat(reloaded.getFundName()).isEqualTo("新名称");
    }

    private FundEntity persistFund(FundCategory category, BigDecimal plannedTotalAmount) {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(category);
        fund.setPlannedTotalAmount(plannedTotalAmount);
        entityManager.persist(fund);
        return fund;
    }
}
