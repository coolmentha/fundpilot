import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, describe, expect, it, vi} from 'vitest';

const state = vi.hoisted(() => ({query: {isLoading: false, isError: false, refetch: vi.fn(), data: undefined}}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn()}));
globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };

const {default: FundHoldingsTab} = await import('../components/FundHoldingsTab.jsx');

const fullQuery = {
    isLoading: false, isError: false, refetch: vi.fn(),
    data: {
        profile: null, scale: null,
        holdings: {status: 'SUCCESS', stale: true, reportDate: '2026-06-30T00:00:00Z', source: {name: '基金报告'}, data: {
            stockHoldings: [
                {kind: 'STOCK', code: '600000', name: '浦发银行', weight: 0.0512},
                {kind: 'UNKNOWN', code: null, name: '未披露', weight: 0.9488},
            ],
            industryHoldings: null, regionHoldings: [{name: '境内', weight: 1}],
            currencyHoldings: [{name: '人民币', weight: 1}],
            disclosedCoverage: 0.0512, lookThrough: true, targetEtfReportDate: '2026-06-30T00:00:00Z',
        }},
        industry: {status: 'SUCCESS', stale: false, source: {name: '天天基金'}, data: {
            holdings: [{name: '制造业', weight: 0.8595}],
        }},
    },
};

describe('FundHoldingsTab', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        root = null;
        container = null;
        state.query = {isLoading: false, isError: false, refetch: vi.fn(), data: undefined};
    });

    it('表格化展示证券持仓、行业/地区/币种分布与穿透口径', async () => {
        state.query = fullQuery;
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(<FundHoldingsTab query={state.query}/>));

        expect(container.textContent).toContain('浦发银行');
        expect(container.textContent).toContain('600000');
        expect(container.textContent).toContain('5.12%');
        expect(container.textContent).toContain('未披露');
        expect(container.textContent).toContain('制造业');
        expect(container.textContent).toContain('85.95%');
        expect(container.textContent).toContain('境内');
        expect(container.textContent).toContain('人民币');
        expect(container.textContent).toContain('目标 ETF 穿透');
        expect(container.textContent).toContain('穿透覆盖率5.12%');
        expect(container.textContent).toContain('数据已过期');
    });

    it('无披露数据与加载失败分别展示占位与错误态', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<FundHoldingsTab query={state.query}/>));
        expect(container.textContent).toContain('暂无持仓披露数据');

        await act(async () => root.unmount());
        state.query = {isLoading: false, isError: true, refetch: vi.fn()};
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<FundHoldingsTab query={state.query}/>));
        expect(container.textContent).toContain('基金研究数据加载失败');
    });
});
