import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('../components/FundIntradayChart.jsx', () => ({default: () => <div>intraday-chart</div>}));
vi.mock('../components/KlineChart.jsx', () => ({default: () => <div>kline-chart</div>}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
window.matchMedia = window.matchMedia || (() => ({matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn()}));
globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };

const {default: FundMarketTab} = await import('./FundMarketTab.jsx');

describe('FundMarketTab', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
    });

    it('默认显示今日分时，并可切换到 K 线 / 走势图', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<FundMarketTab fundId={1} fundSubType="INDEX"/>));
        expect(container.textContent).toContain('intraday-chart');
        expect(container.textContent).not.toContain('kline-chart');

        const klineTab = [...container.querySelectorAll('[role="tab"]')]
            .find((tab) => tab.textContent.includes('K线 / 走势图'));
        await act(async () => klineTab.click());
        expect(container.textContent).toContain('kline-chart');
    });
});
