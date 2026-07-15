package com.fundpilot.backend.market.service.support;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.InvestmentTarget;

import java.util.Locale;

public final class FundMarketDataCapability {

    private FundMarketDataCapability() {
    }

    public static boolean supportsStandardNav(FundEntity fund) {
        return supportsStandardNav(fund.getInvestmentTarget(), fund.getFundName());
    }

    public static boolean supportsStandardNav(InvestmentTarget target, String fundName) {
        if (target == InvestmentTarget.MONEY_MARKET || target == InvestmentTarget.REIT) {
            return false;
        }
        if (fundName == null) {
            return true;
        }
        String normalized = fundName.toUpperCase(Locale.ROOT);
        return !normalized.contains("货币")
                && !normalized.contains("REIT")
                && !normalized.contains("不动产投资信托");
    }
}
