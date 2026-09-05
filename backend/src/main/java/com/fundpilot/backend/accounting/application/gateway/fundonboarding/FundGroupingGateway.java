package com.fundpilot.backend.accounting.application.gateway.fundonboarding;

import java.util.List;

/** Accounting 开户时写入组合分组的最小调用契约。 */
public interface FundGroupingGateway {
    void assignByNames(long ownerId, long portfolioFundId, List<String> names);

    final class Failure extends RuntimeException {
        private final Code code;

        public Failure(Code code, String message) {
            super(message);
            this.code = code;
        }

        public Code code() {
            return code;
        }
    }

    enum Code {
        FUND_GROUP_NAME_INVALID,
        FUND_GROUP_NAME_DUPLICATE,
        FUND_GROUP_NOT_FOUND,
        PORTFOLIO_FUND_NOT_FOUND
    }
}
