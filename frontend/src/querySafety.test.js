import {describe, expect, it} from 'vitest';
import {
    buildFundWatchlistRows,
    estimateStatusText,
    isQueryDataReady,
    mainforceRatio,
    selectContributors,
    selectHoldingRows,
    sortSectors,
} from './querySafety.js';

describe('query safety guards', () => {
    it('我的持仓只保留有正持仓的 HOLDING 基金', () => {
        expect(selectHoldingRows([
            {id: 1, status: 'HOLDING', holdingAmount: 100},
            {id: 2, status: 'CLEARED', holdingAmount: 100},
            {id: 3, status: 'HOLDING', holdingAmount: 0},
        ]).map((row) => row.id)).toEqual([1]);
    });
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
            groups: [{id: 3, name: '核心'}],
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
        expect(row.groups).toEqual([{id: 3, name: '核心'}]);
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

    it('keeps QDII latest confirmed NAV ahead of intraday estimates', () => {
        const [row] = buildFundWatchlistRows([{
            id: 1,
            fundCode: '000001',
            investmentTarget: 'QDII',
            valuationSource: 'LATEST_CONFIRMED_NAV',
            valuationDate: '2026-07-17T00:00:00Z',
            dailyChangePct: 0.1,
            estimateStatus: 'AVAILABLE',
        }], {
            '000001': {estimatedChangePct: 0.02, estimateTime: '2026-07-20 16:00'},
        }, {estimatesFetched: true, estimatesError: false});

        expect(row.changePct).toBe(0.1);
        expect(row.isEstimated).toBe(false);
        expect(row.valuationDate).toBe('2026-07-17T00:00:00Z');

        const [errorRow] = buildFundWatchlistRows([{
            id: 1,
            fundCode: '000001',
            investmentTarget: 'QDII',
            valuationSource: 'LATEST_CONFIRMED_NAV',
            valuationDate: '2026-07-17T00:00:00Z',
            dailyChangePct: 0.1,
            estimateStatus: 'AVAILABLE',
        }], {}, {estimatesFetched: true, estimatesError: true});
        expect(errorRow.changePct).toBe(0.1);
        expect(errorRow.estimateFetchFailed).toBe(false);
    });

    it('keeps confirmed A-share NAV ahead of an intraday estimate', () => {
        const [row] = buildFundWatchlistRows([{
            id: 1,
            fundCode: '510300',
            investmentTarget: 'A_SHARE',
            valuationSource: 'CONFIRMED_NAV',
            valuationDate: '2026-07-22T00:00:00Z',
            dailyChangePct: 0.01,
            isEstimated: false,
            estimateStatus: 'AVAILABLE',
        }], {
            '510300': {estimatedChangePct: 0.02, estimateTime: '2026-07-22 15:00'},
        }, {estimatesFetched: true, estimatesError: false});

        expect(row.changePct).toBe(0.01);
        expect(row.isEstimated).toBe(false);
        expect(row.valuationSource).toBe('CONFIRMED_NAV');
    });

    it('透传持仓表需要的收益和净值字段', () => {
        const [row] = buildFundWatchlistRows([{
            id: 1,
            fundCode: '510300',
            returnRate: 0.1234,
            valuationNav: 4.5678,
            valuationFirstSeenAt: '2026-07-10T07:01:02Z',
        }], {}, {estimatesFetched: false, estimatesError: false});

        expect(row).toMatchObject({
            returnRate: 0.1234,
            valuationNav: 4.5678,
            valuationFirstSeenAt: '2026-07-10T07:01:02Z',
        });
    });

    it('从持仓中选择最大贡献和最大拖累', () => {
        const result = selectContributors([
            {id: 1, status: 'HOLDING', holdingAmount: 100, dailyPnl: 12},
            {id: 2, status: 'HOLDING', holdingAmount: 200, dailyPnl: -8},
            {id: 3, status: 'HOLDING', holdingAmount: 300, dailyPnl: 5},
            {id: 4, status: 'CLEARED', holdingAmount: 100, dailyPnl: 99},
        ]);

        expect(result.contributor.id).toBe(1);
        expect(result.detractor.id).toBe(2);
    });

    it('按完整行业范围排序并计算主力净占比', () => {
        const sectors = [
            {sectorName: 'A', changePct: 0.01, turnover: 1000, mainforceNet: 100},
            {sectorName: 'B', changePct: 0.03, turnover: 500, mainforceNet: -100},
            {sectorName: 'C', changePct: -0.02, turnover: 0, mainforceNet: 20},
        ];

        expect(mainforceRatio(sectors[0])).toBe(0.1);
        expect(mainforceRatio(sectors[2])).toBeNull();
        expect(sortSectors(sectors, 'changePct').map((row) => row.sectorName)).toEqual(['B', 'A', 'C']);
        expect(sortSectors(sectors, 'turnover').map((row) => row.sectorName)).toEqual(['A', 'B', 'C']);
        expect(sortSectors(sectors, 'mainforceRatio').map((row) => row.sectorName)).toEqual(['A', 'B', 'C']);
    });
});
