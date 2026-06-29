package com.fundpilot.backend.fund.enums;

/**
 * 基金子类型——数据源维度的分类（区别于策略参数维度的 {@link FundCategory}）。
 * <ul>
 *   <li>{@link #ETF}：场内交易，可直接拿自身 K 线</li>
 *   <li>{@link #INDEX}：指数基金，看跟踪指数</li>
 *   <li>{@link #INDEX_ENHANCED}：指数增强，看跟踪指数</li>
 *   <li>{@link #ACTIVE}：主动管理，无跟踪指数</li>
 * </ul>
 * 决定行情数据源选择和逻辑止损判定路径（ETF/INDEX/INDEX_ENHANCED 走指数 K 线量能；
 * ACTIVE 走单周跌幅）。详见 ADR-0002 与 CONTEXT.md。
 */
public enum FundSubType {
    ETF,
    INDEX,
    INDEX_ENHANCED,
    ACTIVE
}
