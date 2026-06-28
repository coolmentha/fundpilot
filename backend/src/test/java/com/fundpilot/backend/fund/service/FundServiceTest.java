package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.controller.FundView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import com.fundpilot.backend.user.entity.UserConfigEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * issue #18 计划仓位校验:建仓/编辑基金时 plannedTotalAmount ≤ 总可投资金 × 单品种仓位上限,
 * 防止填一个根本建不了的死状态(CONTEXT.md「计划仓位校验」)。
 * <p>与硬约束互补——计划仓位校验管"意图上限",硬约束管"事实上限"(信号生成时卡实际持仓占比)。
 */
@Transactional
class FundServiceTest extends AbstractIntegrationTest {

    @Autowired FundService fundService;
    @Autowired EntityManager entityManager;

    @Test
    void create_计划仓位超过单只上限_抛异常且不落库() {
        // 总可投资金 100000,单只上限 30% = 30000;填 30000.01 超限
        persistUserConfig(new BigDecimal("100000"));
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, null, null, new BigDecimal("30000.01"));

        assertThatThrownBy(() -> fundService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.PLANNED_AMOUNT_EXCEEDS_LIMIT.name());

        assertThat(countFundByCode("510300")).isZero();
    }

    @Test
    void create_计划仓位等于上限_边界通过() {
        persistUserConfig(new BigDecimal("100000"));
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, null, null, new BigDecimal("30000"));

        FundView view = fundService.create(request);

        assertThat(view.plannedTotalAmount()).isEqualByComparingTo("30000");
    }

    @Test
    void create_计划仓位低于上限_正常创建() {
        persistUserConfig(new BigDecimal("100000"));
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, null, null, new BigDecimal("5000"));

        FundView view = fundService.create(request);

        assertThat(view.plannedTotalAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void create_不填计划仓位_不校验正常创建() {
        // 不 persistUserConfig —— planned=null 应跳过校验,不查 user_config
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, null, null, null);

        FundView view = fundService.create(request);

        assertThat(view.plannedTotalAmount()).isNull();
    }

    @Test
    void create_计划仓位非空但类型为空_抛异常() {
        persistUserConfig(new BigDecimal("100000"));
        // fundSubType 非 null 跳过兜底识别,使 fundCategory 保持 null
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", null, FundSubType.ETF, null, new BigDecimal("5000"));

        assertThatThrownBy(() -> fundService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.FUND_CATEGORY_REQUIRED.name());
    }

    @Test
    void create_用户配置未初始化_抛异常() {
        // 清空本地 DB 残留的 user_config,模拟未初始化
        entityManager.createQuery("DELETE FROM UserConfigEntity").executeUpdate();
        FundCreateRequest request = new FundCreateRequest(
                "510300", "沪深300ETF", FundCategory.BROAD_BASE, null, null, new BigDecimal("5000"));

        assertThatThrownBy(() -> fundService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.USER_CONFIG_NOT_INITIALIZED.name());
    }

    @Test
    void create_行业基金单只上限也是30百分比_无关类型() {
        persistUserConfig(new BigDecimal("100000"));
        // 单只上限 30% 无关类型(曾按行业 15%,已统一);填 30000.01 超限
        FundCreateRequest request = new FundCreateRequest(
                "159825", "半导体ETF", FundCategory.SECTOR, null, null, new BigDecimal("30000.01"));

        assertThatThrownBy(() -> fundService.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.PLANNED_AMOUNT_EXCEEDS_LIMIT.name());
    }

    @Test
    void update_计划仓位改到超限_抛异常且原值未改() {
        persistUserConfig(new BigDecimal("100000"));
        FundEntity fund = persistFund(FundCategory.BROAD_BASE, new BigDecimal("10000")); // 上限 30000
        entityManager.flush();
        FundCreateRequest request = new FundCreateRequest(
                null, null, null, null, null, new BigDecimal("30000.01"));

        assertThatThrownBy(() -> fundService.update(fund.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.PLANNED_AMOUNT_EXCEEDS_LIMIT.name());

        // update 抛异常前已 set 脏值,clear 丢弃持久化上下文不写 DB,重读验证原值未改
        entityManager.clear();
        FundEntity reloaded = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloaded.getPlannedTotalAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void update_计划仓位改到不超限_正常更新() {
        persistUserConfig(new BigDecimal("100000"));
        FundEntity fund = persistFund(FundCategory.BROAD_BASE, new BigDecimal("10000"));
        entityManager.flush();
        FundCreateRequest request = new FundCreateRequest(
                null, null, null, null, null, new BigDecimal("15000")); // 上限 20000

        fundService.update(fund.getId(), request);

        entityManager.flush();
        entityManager.clear();
        FundEntity reloaded = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloaded.getPlannedTotalAmount()).isEqualByComparingTo("15000");
    }

    @Test
    void update_不传计划仓位_保留原值不校验() {
        persistUserConfig(new BigDecimal("100000"));
        FundEntity fund = persistFund(FundCategory.BROAD_BASE, new BigDecimal("10000"));
        entityManager.flush();
        FundCreateRequest request = new FundCreateRequest(
                null, "新名称", null, null, null, null); // planned=null

        fundService.update(fund.getId(), request);

        entityManager.flush();
        entityManager.clear();
        FundEntity reloaded = entityManager.find(FundEntity.class, fund.getId());
        assertThat(reloaded.getPlannedTotalAmount()).isEqualByComparingTo("10000");
        assertThat(reloaded.getFundName()).isEqualTo("新名称");
    }

    private void persistUserConfig(BigDecimal totalInvestableCapital) {
        // 清空本地 DB 残留的 user_config,确保本测试用唯一可控的资金值
        entityManager.createQuery("DELETE FROM UserConfigEntity").executeUpdate();
        UserConfigEntity config = new UserConfigEntity();
        config.setTotalInvestableCapital(totalInvestableCapital);
        entityManager.persist(config);
    }

    private long countFundByCode(String fundCode) {
        return (long) entityManager.createQuery(
                "SELECT COUNT(f) FROM FundEntity f WHERE f.fundCode = :code")
                .setParameter("code", fundCode)
                .getSingleResult();
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
