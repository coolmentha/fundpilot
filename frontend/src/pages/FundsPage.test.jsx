import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('../api/hooks.js', () => ({
    useFunds: () => ({
        data: [{
            id: 1, fundCode: '000001', fundName: '测试基金', fundCategory: 'INDEX', fundSubType: 'INDEX',
            benchmarkIndexCode: '000300', positionWarningEnabled: true, positionWarningRatio: 0.3, groups: [],
        }],
        isLoading: false, refetch: vi.fn(),
    }),
    useFundGroups: () => ({data: []}),
    useDcaBudgetSummary: () => ({data: null, isLoading: false, isError: false, refetch: vi.fn()}),
    useFundSearch: () => ({data: [], isFetching: false}),
    useSaveFund: () => ({mutateAsync: vi.fn(), isPending: false}),
    useVoidPortfolioFund: () => ({mutateAsync: vi.fn(), isPending: false}),
}));

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
window.getComputedStyle = () => ({width: '0px'});
const {default: FundsPage} = await import('./FundsPage.jsx');

describe('FundsPage', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    it('通过 editId 查询参数自动打开目标基金编辑弹窗', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds?editId=1']}><App><FundsPage/></App></MemoryRouter>,
        ));
        await act(async () => new Promise((resolve) => window.setTimeout(resolve, 0)));

        expect(document.body.textContent).toContain('编辑基金');
        expect(document.body.textContent).toContain('测试基金');
    });
});
