import {describe, expect, it, vi} from 'vitest';

vi.mock('./client.js', () => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    del: vi.fn(),
}));

import {del, get, post, put} from './client.js';
import {
    deleteDcaPlan,
    getFundFeeRates,
    getWatchedIndices,
    invalidateDcaBudgetSummary,
    invalidateDcaPlanQueries,
    invalidateConfirmOperationQueries,
    invalidateSignalQueries,
    requestAdminAction,
    replaceWatchedIndices,
    saveAdminUser,
    updatePendingTransaction,
    voidPortfolioFund,
} from './hooks.js';

describe('portfolio fund voiding', () => {
    it('posts the reason and explicit irreversible confirmation', () => {
        voidPortfolioFund({portfolioFundId: 17, reason: '基金代码录入错误'});

        expect(post).toHaveBeenCalledWith('/api/portfolio-funds/17/void', {
            reason: '基金代码录入错误',
            confirmed: true,
        });
    });
});

describe('product fee rates', () => {
    it('queries fees by encoded product code', () => {
        getFundFeeRates('019736 A');

        expect(get).toHaveBeenCalledWith('/api/products/019736%20A/fees');
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

describe('signal query invalidation', () => {
    it('refreshes pending, today, and range queries after a signal response', () => {
        const queryClient = {invalidateQueries: vi.fn()};

        invalidateSignalQueries(queryClient);

        expect(queryClient.invalidateQueries.mock.calls).toEqual([
            [{queryKey: ['signals-pending']}],
            [{queryKey: ['signals-today']}],
            [{queryKey: ['signals-range']}],
        ]);
    });
});

describe('confirm operation query invalidation', () => {
    it('refreshes the new pending transaction and affected fund projections', () => {
        const queryClient = {invalidateQueries: vi.fn()};

        invalidateConfirmOperationQueries(queryClient);

        expect(queryClient.invalidateQueries.mock.calls).toEqual([
            [{queryKey: ['signals-pending']}],
            [{queryKey: ['signals-today']}],
            [{queryKey: ['signals-range']}],
            [{queryKey: ['transactions-pending']}],
            [{queryKey: ['fund-transactions']}],
            [{queryKey: ['funds']}],
        ]);
    });
});

describe('admin actions', () => {
    it.each([
        ['generate', '/api/admin/signals/generate'],
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
