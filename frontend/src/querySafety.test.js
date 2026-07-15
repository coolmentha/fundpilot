import {describe, expect, it} from 'vitest';
import {buildFundWatchlistRows, estimateStatusText, isQueryDataReady} from './querySafety.js';

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

    it('keeps unsupported estimates neutral instead of reporting a fetch failure', () => {
        const [row] = buildFundWatchlistRows([{
            id: 1,
            fundCode: '000009',
            fundName: '测试货币基金',
            estimateStatus: 'UNAVAILABLE',
            estimateFetchFailed: false,
        }], {}, {estimatesFetched: true, estimatesError: false});

        expect(row.changePct).toBeNull();
        expect(row.estimateFetchFailed).toBe(false);
        expect(row.estimateStatus).toBe('UNAVAILABLE');
        expect(estimateStatusText(row.estimateStatus)).toBe('暂无估值');
    });
});
