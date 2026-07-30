package com.fundpilot.backend.accounting.domain.transaction;

/** 账目流水状态；仅 {@code PENDING} 可流转到 {@code CONFIRMED} 或 {@code CANCELLED}。 */
public enum TransactionStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
