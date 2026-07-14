import {describe, expect, it, vi} from 'vitest';

vi.mock('./client.js', () => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    del: vi.fn(),
}));

import {post} from './client.js';
import {invalidateDcaBudgetSummary, invalidateSignalQueries, requestAdminAction} from './hooks.js';

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

        expect(post).toHaveBeenLastCalledWith(path);
    });

    it('rejects unsupported actions instead of falling back to refresh', () => {
        expect(() => requestAdminAction('unknown'))
            .toThrow('Unsupported admin action: unknown');
    });
});

describe('DCA budget summary', () => {
    it('invalidates the global monthly summary after related mutations', () => {
        const queryClient = {invalidateQueries: vi.fn()};

        invalidateDcaBudgetSummary(queryClient);

        expect(queryClient.invalidateQueries).toHaveBeenCalledWith({queryKey: ['dca-budget-summary']});
    });
});
