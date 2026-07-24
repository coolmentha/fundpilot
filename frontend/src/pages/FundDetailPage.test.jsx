import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('../api/hooks.js', () => ({
    useFund: () => ({
        data: {
            id: 1, fundCode: '000001', fundName: '测试基金', fundCategory: 'INDEX', fundSubType: 'INDEX', status: 'HOLDING',
            holdingShares: 100, holdingAmount: 100, totalPnl: 0, valuationSource: 'CONFIRMED_NAV',
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
vi.mock('./FundSignalTab.jsx', () => ({default: () => <div>交易信号内容</div>}));
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
        expect(container.textContent).toContain('待回应信号 1 条');
        expect(container.textContent).toContain('持有不超过 7 天 1.50%');
        expect(container.textContent).toContain('持有超过 7 天 0.00%');
        expect(container.textContent).toContain('销售服务费（年化）');
        expect([...container.querySelectorAll('a')].map((link) => link.getAttribute('href'))).toEqual(expect.arrayContaining([
            '/confirm?fundId=1', '/signals?fundId=1', '/funds?editId=1',
        ]));
    });
});
