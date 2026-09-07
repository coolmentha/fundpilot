import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';

const api = vi.hoisted(() => ({
    useFundGroups: vi.fn(() => ({data: []})),
    useFunds: vi.fn(() => ({data: [{
        id: 101,
        portfolioFundId: 12,
        fundCode: '000001',
        fundName: '测试基金',
        fundSubType: 'INDEX',
        status: 'HOLDING',
        holdingAmount: 100,
        dailyPnl: 1,
    }], isLoading: false, isError: false, refetch: vi.fn()})),
    useFundEstimates: vi.fn(() => ({
        data: {}, isFetched: false, isError: false, refetch: vi.fn(),
    })),
}));
vi.mock('../api/hooks.js', () => api);
vi.mock('antd', async () => {
    const React = await import('react');
    return {
        Table: ({dataSource = [], columns = []}) => React.createElement('div', null,
            dataSource.map((row) => React.createElement('div', {key: row.key},
                columns[0].render(row.fundName, row)))),
    };
});
vi.mock('./FundGroupTabs.jsx', () => ({default: () => null}));

import FundWatchlist from './FundWatchlist.jsx';
import {valuationStatusText} from './valuationStatusText.js';

globalThis.React = React;
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

describe('valuationStatusText', () => {
    it('区分昨日最近净值和今日已确认净值', () => {
        expect(valuationStatusText({
            valuationSource: 'LATEST_CONFIRMED_NAV',
            valuationDate: '2026-07-22T00:00:00Z',
        })).toBe('最近净值 2026-07-22');
        expect(valuationStatusText({valuationSource: 'CONFIRMED_NAV'})).toBe('今日净值已确认');
    });
});

describe('FundWatchlist', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
    });

    it('使用组合基金标识进入基金详情', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<MemoryRouter><FundWatchlist/></MemoryRouter>));

        expect(container.querySelector('a').getAttribute('href')).toBe('/funds/12');
    });
});
