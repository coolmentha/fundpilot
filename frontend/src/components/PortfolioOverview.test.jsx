import React, {act} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {createRoot} from 'react-dom/client';

const {useMarketBreadth, usePortfolioSummary} = vi.hoisted(() => ({
    useMarketBreadth: vi.fn(),
    usePortfolioSummary: vi.fn(),
}));

vi.mock('../api/hooks.js', () => ({useMarketBreadth, usePortfolioSummary}));

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
        usePortfolioSummary.mockReturnValue({data: {}, isLoading: false, isError: false});
        useMarketBreadth.mockReturnValue({data: {
            risingCount: 3814, fallingCount: 1701, limitUpCount: 42, limitDownCount: 25,
        }, isError: false});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<PortfolioOverview/>));

        expect(container.textContent).toContain('涨停 42');
        expect(container.textContent).toContain('跌停 25');
        expect(container.querySelector('.market-breadth-bar').getAttribute('aria-label')).toContain('涨停 42 只');
    });
});
