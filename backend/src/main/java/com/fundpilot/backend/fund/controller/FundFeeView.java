package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.client.RedemptionTier;

import java.math.BigDecimal;
import java.util.List;

/**
 * 基金费率视图 DTO:供前端展示参考费率(申购费率 + 赎回费率阶梯 + 销售服务费)。
 *
 * @param purchaseRate     原申购费率(小数,如 0.015 表 1.5%);null 表未爬到
 * @param discountRate     优惠申购费率(小数,如 0.0015 表 0.15%,天天基金 1折);null 表未爬到
 * @param salesServiceFee  销售服务费率年化(小数,C类非0);null 表未爬到
 * @param redemptionLadder 赎回费率阶梯(按持有期升序);空列表表未爬到
 */
public record FundFeeView(
        BigDecimal purchaseRate,
        BigDecimal discountRate,
        BigDecimal salesServiceFee,
        List<RedemptionTier> redemptionLadder
) {}
