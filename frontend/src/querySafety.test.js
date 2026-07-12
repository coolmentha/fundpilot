import {describe, expect, it} from 'vitest';
import {buildFundWatchlistRows, isQueryDataReady} from './querySafety.js';

describe('query safety guards', () => {
    it('requires successfully loaded query data before enabling destructive actions', () => {
        expect(isQueryDataReady({data: {watchedIndices: []}, isLoading: false, isError: false})).toBe(true);
        expect(isQueryDataReady({data: undefined, isLoading: true, isError: false})).toBe(false);
        expect(isQueryDataReady({data: null, isLoading: false, isError: false})).toBe(false);
        expect(isQueryDataReady({data: {watchedIndices: ['1.000001']}, isLoading: false, isError: true})).toBe(false);
    });

    it('suppresses cached estimates when the estimates query fails', () => {
        const funds = [{
            id: 1,
            fundCode: '000001',
            fundName: '测试基金',
            isEstimated: true,
            dailyChangePct: 1.25,
            dailyPnl: 10,
        }];
        const estimates = {'000001': {estimatedChangePct: 2.5, estimateTime: '2026-07-12T02:00:00Z'}};

        const [row] = buildFundWatchlistRows(funds, estimates, {
            estimatesFetched: true,
            estimatesError: true,
        });

        expect(row.changePct).toBeNull();
        expect(row.isEstimated).toBe(false);
        expect(row.estimateFetchFailed).toBe(true);
        expect(row.estimateTime).toBeUndefined();
    });
});
