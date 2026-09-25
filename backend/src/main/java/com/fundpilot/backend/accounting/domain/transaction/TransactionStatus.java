package com.fundpilot.backend.accounting.domain.transaction;

/** 账目流水状态；仅 {@code PENDING} 可流转到 {@code CONFIRMED} 或 {@code CANCELLED}。 */
public enum TransactionStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    /**
     * 原生 SQL(@Query 注解参数要求编译期常量,无法调用 name())使用的枚举名常量,值必须与枚举常量一致。
     */
    public static final String CONFIRMED_NAME = "CONFIRMED";
    public static final String CANCELLED_NAME = "CANCELLED";
}
