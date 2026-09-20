import {describe, expect, it} from 'vitest';
import {
    buildFundWatchlistRows,
    estimateStatusText,
    filterSectors,
    holdingReturnRate,
    isQueryDataReady,
    mainforceRatio,
    sectorFlowDirection,
    selectContributors,
    selectHoldingRows,
    sortSectors,
} from './querySafety.js';

describe('query safety guards', () => {
    it('多个无旧标识基金在排序和更新后保持组合基金身份', () => {
        const funds = [
            {id: null, portfolioFundId: 12, fundCode: '000012', dailyPnl: -2},
            {id: null, portfolioFundId: 11, fundCode: '000011', dailyPnl: 3},
        ];

        const sorted = buildFundWatchlistRows(funds, {}, {
            estimatesFetched: false,
            estimatesError: false,
        }).sort((left, right) => right.dailyPnl - left.dailyPnl);
        const updated = buildFundWatchlistRows([
            {...funds[0], dailyPnl: 5},
            funds[1],
        ], {}, {estimatesFetched: false, estimatesError: false});

        expect(sorted.map((row) => [row.key, row.id, row.portfolioFundId])).toEqual([
            [11, 11, 11],
            [12, 12, 12],
        ]);
        expect(updated.map((row) => row.key)).toEqual([12, 11]);
    });

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

    it('按当前持仓成本计算持仓收益率', () => {
        const [row] = buildFundWatchlistRows([{
            id: 1,
            fundCode: '510300',
            holdingAmount: 1123.4,
            totalPnl: 123.4,
            returnRate: 0.9999,
            valuationNav: 4.5678,
            valuationFirstSeenAt: '2026-07-10T07:01:02Z',
        }], {}, {estimatesFetched: false, estimatesError: false});

        expect(row).toMatchObject({
            holdingReturnRate: 0.1234,
            valuationNav: 4.5678,
            valuationFirstSeenAt: '2026-07-10T07:01:02Z',
        });
    });

    it('持仓成本不可用时不计算持仓收益率', () => {
        const rows = buildFundWatchlistRows([
            {id: 1, holdingAmount: null, totalPnl: null},
            {id: 2, holdingAmount: 100, totalPnl: 100},
        ], {}, {estimatesFetched: false, estimatesError: false});

        expect(rows.map((row) => row.holdingReturnRate)).toEqual([null, null]);
    });

    it('基金列表和详情页共用持仓收益率口径', () => {
        expect(holdingReturnRate({holdingAmount: 1000, totalPnl: 100})).toBeCloseTo(0.1111, 4);
        expect(holdingReturnRate({holdingAmount: 900, totalPnl: -100})).toBeCloseTo(-0.1, 10);
        expect(holdingReturnRate({holdingAmount: null, totalPnl: 100})).toBeNull();
        expect(holdingReturnRate({holdingAmount: 100, totalPnl: null})).toBeNull();
        expect(holdingReturnRate({holdingAmount: 100, totalPnl: 100})).toBeNull();
        expect(holdingReturnRate(undefined)).toBeNull();
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

    it('主力净额排序按净流入降序、净流出沉底，缺失值排最后', () => {
        const sectors = [
            {sectorName: 'A', mainforceNet: 100},
            {sectorName: 'B', mainforceNet: -300},
            {sectorName: 'C', mainforceNet: 250},
            {sectorName: 'D', mainforceNet: null},
        ];

        expect(sortSectors(sectors, 'mainforceNet').map((row) => row.sectorName)).toEqual(['C', 'A', 'B', 'D']);
    });

    it('资金方向按主力净额正负判定，零值与缺失值不归入任何方向', () => {
        expect(sectorFlowDirection({mainforceNet: 1})).toBe('inflow');
        expect(sectorFlowDirection({mainforceNet: '-203685104'})).toBe('outflow');
        expect(sectorFlowDirection({mainforceNet: 0})).toBeNull();
        expect(sectorFlowDirection({mainforceNet: null})).toBeNull();
        expect(sectorFlowDirection({})).toBeNull();
    });

    it('按资金方向筛选行业，非方向值时返回全量副本', () => {
        const sectors = [
            {sectorName: 'A', mainforceNet: 100},
            {sectorName: 'B', mainforceNet: -100},
            {sectorName: 'C', mainforceNet: 0},
            {sectorName: 'D', mainforceNet: null},
        ];

        expect(filterSectors(sectors, 'inflow').map((row) => row.sectorName)).toEqual(['A']);
        expect(filterSectors(sectors, 'outflow').map((row) => row.sectorName)).toEqual(['B']);
        expect(filterSectors(sectors, 'all').map((row) => row.sectorName)).toEqual(['A', 'B', 'C', 'D']);
        expect(filterSectors(undefined, 'inflow')).toEqual([]);

        const all = filterSectors(sectors, 'all');
        expect(all).not.toBe(sectors);
    });

    it('升序排序让流出最多的行业排最前，缺失值仍排末尾', () => {
        const sectors = [
            {sectorName: 'A', mainforceNet: 100},
            {sectorName: 'B', mainforceNet: -300},
            {sectorName: 'C', mainforceNet: -50},
            {sectorName: 'D', mainforceNet: null},
        ];

        expect(sortSectors(sectors, 'mainforceNet', true).map((row) => row.sectorName))
            .toEqual(['B', 'C', 'A', 'D']);
    });

    it('先按方向筛选再按主力净额排序得到流入榜首与流出榜首', () => {
        const sectors = [
            {sectorName: 'A', mainforceNet: 100},
            {sectorName: 'B', mainforceNet: -300},
            {sectorName: 'C', mainforceNet: 250},
            {sectorName: 'D', mainforceNet: -50},
        ];

        expect(sortSectors(filterSectors(sectors, 'inflow'), 'mainforceNet')[0].sectorName).toBe('C');
        expect(sortSectors(filterSectors(sectors, 'outflow'), 'mainforceNet', true)[0].sectorName).toBe('B');
    });
});
