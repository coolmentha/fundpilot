import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const api = vi.hoisted(() => ({usePortfolioReturns: vi.fn(), usePortfolioReturnTrends: vi.fn()}));
vi.mock('../api/hooks.js', () => api);
vi.mock('antd', async () => {
    const React = await import('react');
    const Box = ({children}) => React.createElement('div', null, children);
    return {
        Alert: ({message}) => React.createElement('div', null, message),
        Button: ({children, onClick}) => React.createElement('button', {onClick}, children),
        Col: Box,
        DatePicker: {RangePicker: Box},
        Row: Box,
        Segmented: Box,
        Skeleton: Box,
        Statistic: ({title, value, formatter}) => React.createElement('div', null,
            title, formatter ? formatter(value) : String(value ?? '')),
        Table: ({dataSource = [], columns = []}) => React.createElement('div', null,
            columns.map((column) => React.createElement('span', {key: column.title}, column.title)),
            dataSource.flatMap((row) => columns.map((column) => React.createElement('div',
                {key: `${row.portfolioFundId}-${column.title}`},
                column.render ? column.render(row[column.dataIndex], row) : row[column.dataIndex])))),
        Typography: {Text: Box},
    };
});

import PortfolioReturns from './PortfolioReturns.jsx';

globalThis.React = React;
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

describe('PortfolioReturns', () => {
    let container;
    let root;

    beforeEach(() => {
        api.usePortfolioReturns.mockReturnValue({
            data: {
                investedAmount: 100, redeemedAmount: 120, feeAmount: 1,
                realizedPnl: 20, unrealizedPnl: 6, totalReturn: 26, returnRate: 0.26,
                realizedComplete: true,
                funds: [{id: 101, portfolioFundId: 12, fundName: '已清仓基金', investedAmount: 100,
                    redeemedAmount: 120, feeAmount: 1, realizedPnl: 20,
                    unrealizedPnl: 0, totalReturn: 20, returnRate: 0.2},
                    {id: 102, portfolioFundId: 13, fundCode: '000003', fundName: '缺净值基金', open: true,
                        investedAmount: 100, redeemedAmount: 0, feeAmount: 0, realizedPnl: 0,
                        unrealizedPnl: null, totalReturn: null, returnRate: null}],
            },
            isLoading: false,
            isError: false,
            refetch: vi.fn(),
        });
        api.usePortfolioReturnTrends.mockReturnValue({data: {
            valuationComplete: false,
            missingFundCodes: ['000002'],
            dataSufficient: true,
            intervalReturn: 6,
            intervalReturnRate: 0.06,
            investedAmount: 0,
            redeemedAmount: 0,
            maximumDrawdown: 2,
            points: [
                {date: '2026-07-28T00:00:00Z', totalReturn: 20},
                {date: '2026-07-29T00:00:00Z', totalReturn: 26},
            ],
        }, isLoading: false, isError: false, refetch: vi.fn()});
    });

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
    });

    it('shows realized and unrealized returns, persisted trend, and partial valuation coverage', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<MemoryRouter><PortfolioReturns/></MemoryRouter>));

        expect(container.textContent).toContain('已实现盈亏');
        expect(container.textContent).toContain('未实现盈亏');
        expect(container.textContent).toContain('累计收益率');
        expect(container.textContent).toContain('已清仓基金');
        expect(container.textContent).toContain('1 只基金当前净值未覆盖：000003');
        expect(container.textContent).toContain('1 只基金净值未覆盖本区间');
        expect(container.querySelector('[aria-label="组合累计收益趋势"]')).not.toBeNull();
        expect(container.querySelector('a').getAttribute('href')).toBe('/funds/101');
    });

    it('distinguishes trend loading and failure from an empty result', async () => {
        const retry = vi.fn();
        api.usePortfolioReturnTrends.mockReturnValue({data: undefined, isLoading: false,
            isError: true, refetch: retry});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<MemoryRouter><PortfolioReturns/></MemoryRouter>));

        expect(container.textContent).toContain('历史趋势加载失败');
        expect(container.textContent).not.toContain('0 只基金净值未覆盖');
        await act(async () => container.querySelector('button').click());
        expect(retry).toHaveBeenCalledOnce();
    });

    it('shows a loading state before trend data arrives', async () => {
        api.usePortfolioReturnTrends.mockReturnValue({data: undefined, isLoading: true,
            isError: false, refetch: vi.fn()});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<MemoryRouter><PortfolioReturns/></MemoryRouter>));

        expect(container.querySelector('[aria-label="历史趋势加载中"]')).not.toBeNull();
        expect(container.textContent).not.toContain('趋势数据将在首个净值确认快照后显示');
    });
});
