import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';

vi.mock('../components/FundIntradayChart.jsx', () => ({default: ({portfolioFundId}) => <div>intraday-chart-{portfolioFundId}</div>}));
vi.mock('../components/KlineChart.jsx', () => ({default: ({portfolioFundId}) => <div>kline-chart-{portfolioFundId}</div>}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn()}));
globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };

const {default: FundMarketTab} = await import('./FundMarketTab.jsx');

describe('FundMarketTab', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        vi.unstubAllGlobals();
    });

    it('默认显示今日分时，并可切换到 K 线 / 走势图', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<QueryClientProvider client={new QueryClient()}><FundMarketTab portfolioFundId={41} fundSubType="INDEX"/></QueryClientProvider>));
        expect(container.textContent).toContain('intraday-chart');
        expect(container.textContent).not.toContain('kline-chart');

        const klineTab = [...container.querySelectorAll('[role="tab"]')]
            .find((tab) => tab.textContent.includes('K线 / 走势图'));
        await act(async () => klineTab.click());
        expect(container.textContent).toContain('kline-chart');
        expect(container.textContent).toContain('kline-chart-41');
    });

    it('按组合基金刷新，失败显示错误且可重试，成功后失效行情缓存', async () => {
        const client = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
        const invalidate = vi.spyOn(client, 'invalidateQueries');
        const fetch = vi.fn()
            .mockResolvedValueOnce({ok: false, status: 400, json: async () => ({success: false,
                code: 'FUND_NOT_FOUND', message: '组合基金不存在或已作废'})})
            .mockResolvedValueOnce({ok: true, json: async () => ({success: true, data: {portfolioFundId: 41}})});
        vi.stubGlobal('fetch', fetch);
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<QueryClientProvider client={client}><FundMarketTab portfolioFundId={41}/></QueryClientProvider>));
        const refresh = () => [...container.querySelectorAll('button')].find(b => b.textContent.includes('刷新行情'));
        await act(async () => { refresh().click(); await new Promise(r => setTimeout(r, 40)); });
        expect(fetch).toHaveBeenCalledWith('/api/portfolio-funds/41/market-data/refresh', expect.objectContaining({method: 'POST'}));
        expect(container.textContent).toContain('组合基金不存在或已作废');
        expect(invalidate).not.toHaveBeenCalled();
        await act(async () => { refresh().click(); await new Promise(r => setTimeout(r, 40)); });
        expect(container.textContent).not.toContain('组合基金不存在或已作废');
        expect(invalidate).toHaveBeenCalledWith({queryKey: ['funds', 41]});
        expect(invalidate).toHaveBeenCalledWith({queryKey: ['market-today', 41]});
    });
});
