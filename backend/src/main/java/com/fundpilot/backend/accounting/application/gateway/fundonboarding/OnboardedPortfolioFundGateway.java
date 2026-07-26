package com.fundpilot.backend.accounting.application.gateway.fundonboarding;

import java.math.BigDecimal;

/**
 * 开户用例对组合关系创建的出站契约。方向为 {@code Accounting -> Portfolio}，
 * Portfolio 不反向依赖 Accounting。
 */
public interface OnboardedPortfolioFundGateway {

    /** 在调用方同一本地事务内建立组合关系；失败抛 {@link Rejected}，由 Handler 转为账目错误语义。 */
    OnboardedPortfolioFund track(long ownerId, long fundProductId, boolean positionWarningEnabled,
                                 BigDecimal positionWarningRatio);

    record OnboardedPortfolioFund(long portfolioFundId, long ownerId, long fundProductId) {
    }

    /** 组合侧可预期的失败原因，已由 GatewayImpl 转换为账目语言。 */
    final class Rejected extends RuntimeException {
        private final Reason reason;

        public Rejected(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }

        public enum Reason {
            PRODUCT_NOT_FOUND,
            ALREADY_TRACKED,
            INVALID_POSITION_WARNING
        }
    }
}
