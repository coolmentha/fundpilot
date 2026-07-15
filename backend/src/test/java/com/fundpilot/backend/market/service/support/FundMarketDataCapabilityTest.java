package com.fundpilot.backend.market.service.support;

import com.fundpilot.backend.fund.enums.InvestmentTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FundMarketDataCapabilityTest {

    @Test
    void 货币和REIT不支持普通净值模型() {
        assertThat(FundMarketDataCapability.supportsStandardNav(InvestmentTarget.MONEY_MARKET, "测试基金")).isFalse();
        assertThat(FundMarketDataCapability.supportsStandardNav(InvestmentTarget.REIT, "测试基金")).isFalse();
        assertThat(FundMarketDataCapability.supportsStandardNav(null, "某某货币A")).isFalse();
        assertThat(FundMarketDataCapability.supportsStandardNav(null, "某某REIT")).isFalse();
    }

    @Test
    void 普通和QDII基金支持普通净值模型() {
        assertThat(FundMarketDataCapability.supportsStandardNav(InvestmentTarget.STOCK, "沪深300ETF")).isTrue();
        assertThat(FundMarketDataCapability.supportsStandardNav(InvestmentTarget.QDII, "全球精选QDII")).isTrue();
    }
}
