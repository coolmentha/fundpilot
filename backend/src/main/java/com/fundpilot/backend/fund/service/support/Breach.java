package com.fundpilot.backend.fund.service.support;

import java.math.BigDecimal;

/**
 * 单条硬约束违反记录:约束名 + 实际值 + 上限值。
 * <p>{@link HardConstraintChecker#check5} 返回的列表元素,空列表表示全部通过。
 *
 * @param name   约束名(BUILD_RATIO / SINGLE_POSITION_LIMIT 等)
 * @param actual 实际值
 * @param limit  上限值
 */
public record Breach(String name, BigDecimal actual, BigDecimal limit) {
}
