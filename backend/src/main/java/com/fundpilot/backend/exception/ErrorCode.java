package com.fundpilot.backend.exception;

/**
 * 业务错误码枚举,集中定义所有 {@link BusinessException} 携带的机器可读 code。
 * <p>替代散落的字符串字面量,消除拼写漂移,作为前后端错误码约定的单一事实源。
 * <p>所有业务异常统一 HTTP 400；请求边界错误可由过滤器映射 401/503，404 留给框架，500 留给未预期问题。
 */
public enum ErrorCode {
    // 资源未找到(业务问题,400)
    FUND_NOT_FOUND,
    STRATEGY_NOT_FOUND,
    TRANSACTION_NOT_FOUND,
    SIGNAL_LOG_NOT_FOUND,
    DCA_PLAN_NOT_FOUND,
    ENTITY_NOT_FOUND,
    MISSING_FUND_IDENTITY,
    // 输入校验非法(400)
    FUND_CATEGORY_REQUIRED,
    MANUAL_TRANSACTION_FIELD_REQUIRED,
    OPENED_AT_IN_FUTURE,
    COST_PER_SHARE_INVALID,
    STRATEGY_PARAM_INVALID,
    DCA_PLAN_INVALID,
    SIGNAL_OPERATION_VALUE_INVALID,

    // 交易/信号状态非法(400)
    TRANSACTION_ALREADY_CONFIRMED,
    TRANSACTION_ALREADY_CANCELLED,
    INVALID_SIGNAL_TYPE,
    MISSING_TRIGGER_TIER,
    INVALID_TRIGGER_TIER,
    MISSING_ACTUAL_AMOUNT,
    MISSING_ACTUAL_SHARES,
    UNSUPPORTED_SELL_REASON,
    SIGNAL_ALREADY_RESPONDED,
    SIGNAL_ALREADY_IGNORED,
    SIGNAL_EXPIRED,
    SIGNAL_FUND_MISMATCH,
    ILLEGAL_STATE_TRANSITION,
    INSUFFICIENT_LOTS,
    INSUFFICIENT_HOLDING_SHARES,

    // 数据源(400)
    NAV_HISTORY_EMPTY,
    MARKET_DATA_ALL_SOURCES_FAILED,

    // 管理端鉴权(401/503,由请求过滤器直接映射 HTTP 状态)
    ADMIN_UNAUTHORIZED,
    ADMIN_AUTH_NOT_CONFIGURED,

    // 兜底(500)
    INTERNAL_ERROR;

    public BusinessException toException(String message) {
        return new BusinessException(this, message);
    }
}
