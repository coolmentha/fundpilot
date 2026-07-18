import {describe, expect, it, vi} from 'vitest';

vi.mock('./client.js', () => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    del: vi.fn(),
}));

import {del, post, put} from './client.js';
import {
    deleteDcaPlan,
    invalidateDcaBudgetSummary,
    invalidateDcaPlanQueries,
    invalidateSignalQueries,
    requestAdminAction,
    updatePendingTransaction,
} from './hooks.js';

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

describe('admin actions', () => {
    it.each([
        ['generate', '/api/admin/signals/generate'],
        ['confirm-nav', '/api/admin/transactions/confirm-nav'],
        ['sync-dict', '/api/admin/fund-dict/sync'],
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

        expect(del).toHaveBeenCalledWith('/api/dca-plans/7');
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
