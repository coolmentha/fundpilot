import React, {act} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {createRoot} from 'react-dom/client';
import {MemoryRouter} from 'react-router-dom';

const {useFunds, useMarketStatus} = vi.hoisted(() => ({useFunds: vi.fn(), useMarketStatus: vi.fn()}));
vi.mock('../api/hooks.js', () => ({useFunds, useMarketStatus}));
vi.mock('../components/IndexTicker.jsx', () => ({default: () => <div>指数</div>}));
vi.mock('../components/PortfolioOverview.jsx', () => ({default: () => <div>总览数据</div>}));
vi.mock('../components/MarketVolumePrice.jsx', () => ({default: () => <div>市场量价</div>}));
vi.mock('../components/FundWatchlist.jsx', () => ({default: () => <div>持仓表</div>}));
vi.mock('../components/SectorPerformance.jsx', () => ({default: () => <div>行业表</div>}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;

const {default: MarketDashboardPage} = await import('./MarketDashboardPage.jsx');

describe('MarketDashboardPage', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        vi.useRealTimers();
        vi.clearAllMocks();
    });

    it('展示后端市场状态、快照时间和最大贡献拖累', async () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-08-11T08:00:00Z'));
        useMarketStatus.mockReturnValue({data: {
            marketState: 'CLOSED', updatedAt: '2026-08-11T07:00:08Z',
        }});
        useFunds.mockReturnValue({data: [
            {id: 1, fundName: '贡献基金', status: 'HOLDING', holdingAmount: 100, dailyPnl: 12.3},
            {id: 2, fundName: '拖累基金', status: 'HOLDING', holdingAmount: 100, dailyPnl: -8.2},
        ]});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<MemoryRouter><MarketDashboardPage/></MemoryRouter>));

        expect(container.textContent).toContain('A 股已收盘');
        expect(container.textContent).toContain('数据截至 15:00:08');
        expect(container.textContent).toContain('市场量价');
        expect(container.textContent).toContain('今日最大贡献贡献基金');
        expect(container.textContent).toContain('今日最大拖累拖累基金');
    });

    it('跨日快照展示完整日期', async () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-08-12T01:00:00Z'));
        useMarketStatus.mockReturnValue({data: {
            marketState: 'PRE_OPEN', updatedAt: '2026-08-11T07:00:08Z',
        }});
        useFunds.mockReturnValue({data: []});
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<MemoryRouter><MarketDashboardPage/></MemoryRouter>));

        expect(container.textContent).toContain('数据截至 2026-08-11 15:00:08');
    });
});
