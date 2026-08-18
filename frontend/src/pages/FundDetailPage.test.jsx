import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('../api/hooks.js', () => ({
    useFund: () => ({
        data: {
            id: 1, fundCode: '000001', fundName: '测试基金', fundCategory: 'INDEX', fundSubType: 'INDEX', status: 'HOLDING',
            holdingShares: 100, holdingAmount: 80, totalPnl: -20, dailyPnl: 1.5, dailyChangePct: 0.02,
            valuationSource: 'CONFIRMED_NAV',
            valuationDate: '2026-07-24T00:00:00Z', valuationNav: 1, positionWarningEnabled: true,
        },
        isLoading: false, isError: false, refetch: vi.fn(),
    }),
    useFundFeeRates: () => ({
        data: {
            purchaseRate: 0.015, discountRate: 0.0015, salesServiceFee: 0.003,
            redemptionLadder: [{maxDays: 7, rate: 0.015}, {maxDays: null, rate: 0}],
        },
    }),
    usePendingTransactions: () => ({data: [{id: 1, fundId: 1}, {id: 2, fundId: 1}, {id: 3, fundId: 2}]}),
    usePendingSignals: () => ({data: [{id: 1, fundId: 1}, {id: 2, fundId: 2}]}),
}));
vi.mock('./FundTransactionTab.jsx', () => ({default: () => <div>交易流水内容</div>}));
vi.mock('./FundStrategyTab.jsx', () => ({default: () => <div>策略参数内容</div>}));
vi.mock('./FundSignalTab.jsx', () => ({default: () => <div>纪律建议内容</div>}));
vi.mock('./FundMarketTab.jsx', () => ({default: () => <div>行情指标内容</div>}));
vi.mock('./FundDcaTab.jsx', () => ({default: () => <div>定投计划内容</div>}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}));
window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
};
const {default: FundDetailPage} = await import('./FundDetailPage.jsx');

describe('FundDetailPage', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        root = null;
        container = null;
    });

    it('展示当前基金待办、完整费率并提供操作入口', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds/1']}>
                <Routes><Route path="/funds/:fundId" element={<FundDetailPage/>}/></Routes>
            </MemoryRouter>,
        ));

        expect(container.textContent).toContain('待确认交易 2 笔');
        expect(container.textContent).toContain('待回应建议 1 条');
        expect(container.textContent).toContain('持有不超过 7 天 1.50%');
        expect(container.textContent).toContain('持有超过 7 天 0.00%');
        expect(container.textContent).toContain('销售服务费（年化）');
        expect(container.textContent).toContain('总盈亏');
        expect(container.textContent).toContain('-¥20.00');
        expect(container.textContent).toContain('(-20.00%)');
        expect(container.textContent).toContain('今日盈亏');
        expect(container.textContent).toContain('+¥1.50');
        expect(container.textContent).toContain('(+2.00%)');
        expect(container.textContent).not.toContain('今日涨跌');
        expect([...container.querySelectorAll('.ant-tabs-tab-btn')].map((tab) => tab.textContent)).toEqual([
            '行情指标', '交易流水', '策略参数', '纪律建议', '定投计划',
        ]);
        expect(container.querySelector('.ant-tabs-tab-active')?.textContent).toBe('行情指标');
        expect(container.textContent).toContain('行情指标内容');
        expect([...container.querySelectorAll('a')].map((link) => link.getAttribute('href'))).toEqual(expect.arrayContaining([
            '/confirm?fundId=1', '/advice?fundId=1', '/funds?editId=1',
        ]));
    });
});
