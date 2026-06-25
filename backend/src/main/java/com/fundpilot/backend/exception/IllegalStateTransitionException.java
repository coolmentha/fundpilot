package com.fundpilot.backend.exception;

/**
 * 状态机非法跃迁异常,code 固定 {@code ILLEGAL_STATE_TRANSITION},{@code message} 形如
 * {@code "PENDING → CONFIRMED 不是合法跃迁"}。{@link GlobalExceptionHandler} 映射 HTTP 409。
 *
 * @param from 起始状态
 * @param to 目标状态
 */
public class IllegalStateTransitionException extends BusinessException {

    public IllegalStateTransitionException(String from, String to) {
        super("ILLEGAL_STATE_TRANSITION", from + " → " + to + " 不是合法跃迁");
    }
}
