import {describe, expect, it, vi} from 'vitest';

vi.mock('./client.js', () => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    del: vi.fn(),
}));

import {del, get, post, put} from './client.js';
import {
    createPortfolioFund,
    createManualTransaction,
    deleteDcaPlan,
    getPortfolioFundTransactions,
    getPortfolioInsightFund,
    getFundFeeRates,
    getYangjibaoSessions,
    getWatchedIndices,
    invalidateDcaBudgetSummary,
    invalidateDcaPlanQueries,
    requestAdminAction,
    replaceWatchedIndices,
    replacePortfolioFundGroups,
    saveAdminUser,
    updatePortfolioFundCostBasis,
    updatePortfolioFundWarning,
    updatePendingTransaction,
    voidPortfolioFund,
    createAlertRule,
    deleteAlertRule,
    getAlertIndicatorMetadata,
    getAlertNotifications,
    getAlertRules,
    invalidateAlertRuleQueries,
    previewAlertRule,
    setAlertRuleEnabled,
    updateAlertRule,
    updateSiteEmail,
    realtimeQueryOptions,
} from './hooks.js';

describe('non-realtime query options', () => {
    it('does not poll on a fixed interval in the background', () => {
        expect(realtimeQueryOptions).not.toHaveProperty('refetchInterval');
        expect(realtimeQueryOptions.refetchIntervalInBackground).toBe(false);
    });

    it('still refetches when the window regains focus', () => {
        expect(realtimeQueryOptions.refetchOnWindowFocus).toBe(true);
    });
});

describe('portfolio fund onboarding', () => {
    it('creates a portfolio fund through the accounting-owned endpoint', () => {
        const body = {
            fundProductId: 23,
            positionWarningEnabled: true,
            positionWarningRatio: 0.3,
            initialHoldingShares: null,
            costPerShare: null,
            openedAt: null,
            groupNames: [],
        };

        createPortfolioFund(body);

        expect(post).toHaveBeenCalledWith('/api/portfolio-funds', body);
    });
});

describe('portfolio fund transaction routes', () => {
    it('lists and records transactions with portfolio fund identifiers', () => {
        const body = {source: 'TRANSFER_OUT', shares: 10, targetPortfolioFundId: 42};

        getPortfolioFundTransactions(41);
        createManualTransaction({portfolioFundId: 41, body});

        expect(get).toHaveBeenLastCalledWith('/api/portfolio-funds/41/transactions');
        expect(post).toHaveBeenLastCalledWith('/api/portfolio-funds/41/transactions', body);
        expect(body).not.toHaveProperty('targetFundId');
    });
});

describe('portfolio fund voiding', () => {
    it('posts the reason and explicit irreversible confirmation', () => {
        voidPortfolioFund({portfolioFundId: 17, reason: '基金代码录入错误'});

        expect(post).toHaveBeenCalledWith('/api/portfolio-funds/17/void', {
            reason: '基金代码录入错误',
            confirmed: true,
        });
    });
});

describe('portfolio fund configuration', () => {
    it('updates warning through the portfolio fund endpoint', () => {
        updatePortfolioFundWarning({portfolioFundId: 41, body: {enabled: true, ratio: 0.25}});

        expect(put).toHaveBeenCalledWith('/api/portfolio-funds/41/position-warning', {
            enabled: true,
            ratio: 0.25,
        });
    });

    it('replaces groups through the portfolio fund endpoint', () => {
        replacePortfolioFundGroups({portfolioFundId: 41, body: {groupNames: ['核心', '卫星']}});

        expect(put).toHaveBeenCalledWith('/api/portfolio-funds/41/groups', {
            groupNames: ['核心', '卫星'],
        });
    });

    it('updates cost basis through the dedicated portfolio fund endpoint', () => {
        updatePortfolioFundCostBasis({portfolioFundId: 41, body: {costPerShare: 3.4}});

        expect(put).toHaveBeenCalledWith('/api/portfolio-funds/41/cost-basis', {
            costPerShare: 3.4,
        });
    });
});

describe('portfolio fund insights routes', () => {
    it('uses the portfolio fund identifier for return details', () => {
        getPortfolioInsightFund(41);

        expect(get).toHaveBeenLastCalledWith('/api/insights/portfolio/funds/41');
    });
});

describe('product optional data', () => {
    it('queries fees, research, and open lots by their owner identifiers', async () => {
        const {getFundResearch, getFundOpenLots} = await import('./hooks.js');
        getFundFeeRates('019736 A');
        getFundResearch('019736 A');
        getFundOpenLots(41);

        expect(get.mock.calls.slice(-3)).toEqual([
            ['/api/products/019736%20A/fees'],
            ['/api/products/019736%20A/research'],
            ['/api/portfolio-funds/41/open-lots'],
        ]);
    });
});

describe('watched indices', () => {
    it('uses the MarketData owned endpoint for reads and replacements', () => {
        getWatchedIndices();
        replaceWatchedIndices(['1.000001', '1.000300']);

        expect(get).toHaveBeenCalledWith('/api/market-data/watched-indices');
        expect(put).toHaveBeenCalledWith('/api/market-data/watched-indices', {
            indexCodes: ['1.000001', '1.000300'],
        });
    });
});

describe('养基宝导入会话', () => {
    it('lists the current user sessions through the importing endpoint', () => {
        getYangjibaoSessions();

        expect(get).toHaveBeenLastCalledWith('/api/imports/yangjibao/sessions');
    });
});

describe('admin actions', () => {
    it.each([
        ['confirm-nav', '/api/admin/transactions/confirm-nav'],
        ['sync-dict', '/api/admin/products/catalog/sync'],
        ['sync-calendar', '/api/admin/market-data/sync-trading-calendar'],
        ['refresh', '/api/admin/market-data/refresh'],
    ])('routes %s through the authenticated API client', (action, path) => {
        requestAdminAction(action);

        expect(post).toHaveBeenLastCalledWith(
            path,
            undefined,
            action === 'refresh' ? {timeoutMs: 120_000} : {},
        );
    });

    it('rejects unsupported actions instead of falling back to refresh', () => {
        expect(() => requestAdminAction('unknown'))
            .toThrow('Unsupported admin action: unknown');
    });
});

describe('admin users', () => {
    it('posts the entered credentials as JSON body', () => {
        const body = {username: 'alice', password: 'secret', role: 'USER'};

        saveAdminUser('/api/admin/users', body);

        expect(post).toHaveBeenCalledWith('/api/admin/users', body);
    });
});

describe('pending transaction update', () => {
    it('uses the transaction update endpoint', () => {
        const body = {amount: null, shares: '605.36974183', tradeDate: '2026-07-18T00:00:00+08:00'};

        updatePendingTransaction(7, body);

        expect(put).toHaveBeenCalledWith('/api/transactions/7', body);
    });
});

describe('DCA budget summary', () => {
    it('invalidates the global monthly summary after related mutations', () => {
        const queryClient = {invalidateQueries: vi.fn()};

        invalidateDcaBudgetSummary(queryClient);

        expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['dca-budget-summary']});
    });
});

describe('DCA plan deletion', () => {
    it('uses DELETE for the selected plan', () => {
        deleteDcaPlan(7);

        expect(del).toHaveBeenCalledWith('/api/investment-plans/7');
    });

    it('invalidates all plan projections and budget summary', () => {
        const queryClient = {invalidateQueries: vi.fn()};

        invalidateDcaPlanQueries(queryClient);

        expect(queryClient.invalidateQueries.mock.calls).toEqual([
            [{queryKey: ['dca-plans']}],
            [{queryKey: ['dca-active']}],
            [{queryKey: ['dca-budget-summary']}],
        ]);
    });
});

describe('price alert rules', () => {
    const body = {
        scope: 'GLOBAL', portfolioFundId: null, match: 'ALL',
        conditions: [{indicator: 'DAILY_CHANGE', relation: 'ABOVE', params: {}, value: 0.05}],
        enabled: true,
    };

    it('reads rules and notifications from the alerting endpoints', () => {
        getAlertRules();
        getAlertNotifications(20);

        expect(get.mock.calls.slice(-2)).toEqual([
            ['/api/alert-rules'],
            ['/api/alert-notifications?limit=20'],
        ]);
    });

    it('reads the indicator metadata that drives the condition builder', () => {
        getAlertIndicatorMetadata();

        expect(get).toHaveBeenLastCalledWith('/api/alert-rules/indicators');
    });

    it('previews a draft rule without saving it', () => {
        previewAlertRule(body);

        expect(post).toHaveBeenLastCalledWith('/api/alert-rules/preview', body);
    });

    it('creates a rule through the alerting endpoint', () => {
        createAlertRule(body);

        expect(post).toHaveBeenLastCalledWith('/api/alert-rules', body);
    });

    it('updates a rule by id', () => {
        updateAlertRule({id: 7, body});

        expect(put).toHaveBeenLastCalledWith('/api/alert-rules/7', body);
    });

    it('toggles a rule through the action endpoint', () => {
        setAlertRuleEnabled({id: 7, action: 'disable'});

        expect(post).toHaveBeenLastCalledWith('/api/alert-rules/7/disable');
    });

    it('deletes a rule by id', () => {
        deleteAlertRule(7);

        expect(del).toHaveBeenLastCalledWith('/api/alert-rules/7');
    });

    it('invalidates the rule list after rule mutations', () => {
        const queryClient = {invalidateQueries: vi.fn()};

        invalidateAlertRuleQueries(queryClient);

        expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['alert-rules']});
    });

    it('updates the reminder email through the auth endpoint', () => {
        updateSiteEmail('alice@example.com');

        expect(put).toHaveBeenLastCalledWith('/api/auth/email', {email: 'alice@example.com'});
    });
});
