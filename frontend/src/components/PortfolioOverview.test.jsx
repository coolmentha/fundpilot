import React, {act} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {createRoot} from 'react-dom/client';

const {useFunds, useMarketBreadth, usePortfolioSummary} = vi.hoisted(() => ({
    useFunds: vi.fn(),
    useMarketBreadth: vi.fn(),
    usePortfolioSummary: vi.fn(),
}));

vi.mock('../api/hooks.js', () => ({useFunds, useMarketBreadth, usePortfolioSummary}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;

const {default: PortfolioOverview} = await import('./PortfolioOverview.jsx');

describe('PortfolioOverview', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        vi.clearAllMocks();
    });

    it('展示完整市场宽度中的涨停和跌停家数', async () => {
        useFunds.mockReturnValue({data: []});
        usePortfolioSummary.mockReturnValue({data: {}, isLoading: false, isError: false});
        useMarketBreadth.mockReturnValue({data: {
            risingCount: 3814, flatCount: 153, fallingCount: 1701, limitUpCount: 42, limitDownCount: 25,
        }, isError: false});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<PortfolioOverview/>));

        expect(container.textContent).toContain('涨停 42');
        expect(container.textContent).toContain('跌停 25');
        expect(container.textContent).toContain('平盘 153');
        expect(container.querySelector('.market-breadth-bar').getAttribute('aria-label')).toContain('涨停 42 只');
    });

    it('独立展示基金涨跌、盈亏和未覆盖范围', async () => {
        usePortfolioSummary.mockReturnValue({data: {
            holdingFundCount: 4, dailyCoveredFundCount: 3, dailyPnlTotal: 1,
            risingFundCount: 1, fallingFundCount: 1,
            profitableFundCount: 1, losingFundCount: 1,
        }, isLoading: false, isError: false});
        useFunds.mockReturnValue({data: [
            {positionStatus: 'OPEN', fundCode: '000001', dailyPnl: 2, unrealizedPnl: -10},
            {positionStatus: 'OPEN', fundCode: '000002', dailyPnl: -1, unrealizedPnl: 10},
            {positionStatus: 'OPEN', fundCode: '000003', dailyPnl: null, unrealizedPnl: null},
            {positionStatus: 'OPEN', fundCode: '000004', dailyPnl: 0, unrealizedPnl: null},
        ]});
        useMarketBreadth.mockReturnValue({data: undefined, isError: false});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<PortfolioOverview/>));

        expect(container.textContent).toContain('上涨 1 · 下跌 1');
        expect(container.textContent).toContain('盈利 1 · 亏损 1');
        expect(container.textContent).toContain('已覆盖 3 / 4 只');
        expect(container.textContent).toContain('2 只基金收益未完全覆盖');
        expect(container.textContent).toContain('未覆盖：000003、000004');
    });
});
