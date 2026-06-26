package com.fundpilot.backend.exception;

/**
 * 业务错误码枚举,集中定义所有 {@link BusinessException} 携带的机器可读 code。
 * <p>替代散落的字符串字面量,消除拼写漂移,作为前后端错误码约定的单一事实源。
 * <p>所有业务问题统一 HTTP 400,404 留给框架处理路由不存在,500 留给未预期的代码问题。
 */
public enum ErrorCode {
    // 资源未找到(业务问题,400)
    FUND_NOT_FOUND,
    STRATEGY_NOT_FOUND,
    TRANSACTION_NOT_FOUND,
    SIGNAL_LOG_NOT_FOUND,
    USER_CONFIG_NOT_INITIALIZED,
    ENTITY_NOT_FOUND,

    // 交易/信号状态非法(400)
    TRANSACTION_ALREADY_CONFIRMED,
    TRANSACTION_ALREADY_CANCELLED,
    INVALID_SIGNAL_TYPE,
    MISSING_TRIGGER_TIER,
    INVALID_TRIGGER_TIER,
    MISSING_ACTUAL_AMOUNT,
    MISSING_ACTUAL_SHARES,
    UNSUPPORTED_SELL_REASON,
    NO_VALID_BACKTEST,
    ILLEGAL_STATE_TRANSITION,

    // 数据源(400)
    NAV_HISTORY_EMPTY,
    MARKET_DATA_ALL_SOURCES_FAILED,

    // 兜底(500)
    INTERNAL_ERROR;

    public BusinessException toException(String message) {
        return new BusinessException(this, message);
    }
}
