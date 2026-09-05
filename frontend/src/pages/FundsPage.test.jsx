import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const {saveFund, updateWarning, replaceGroups, updateCostBasis, state} = vi.hoisted(() => ({
    saveFund: vi.fn(),
    updateWarning: vi.fn(),
    replaceGroups: vi.fn(),
    updateCostBasis: vi.fn(),
    state: {funds: []},
}));

vi.mock('../api/hooks.js', () => ({
    useFunds: () => ({
        data: state.funds,
        isLoading: false, refetch: vi.fn(),
    }),
    useFundGroups: () => ({data: []}),
    useDcaBudgetSummary: () => ({data: null, isLoading: false, isError: false, refetch: vi.fn()}),
    useFundSearch: () => ({data: [], isFetching: false}),
    useSaveFund: () => ({mutateAsync: saveFund, isPending: false}),
    useUpdatePortfolioFundWarning: () => ({mutateAsync: updateWarning, isPending: false}),
    useReplacePortfolioFundGroups: () => ({mutateAsync: replaceGroups, isPending: false}),
    useUpdatePortfolioFundCostBasis: () => ({mutateAsync: updateCostBasis, isPending: false}),
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

    beforeEach(() => {
        saveFund.mockReset();
        updateWarning.mockReset();
        replaceGroups.mockReset();
        updateCostBasis.mockReset();
        state.funds = [{
            id: 1, portfolioFundId: 11, fundCode: '000001', fundName: '测试基金',
            fundCategory: 'INDEX', fundSubType: 'INDEX', benchmarkIndexCode: '000300',
            positionWarningEnabled: true, positionWarningRatio: 0.3,
            holdingShares: 100, costPerShare: 1.2, groups: [],
        }];
    });

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
        expect(Number(document.querySelector('#costPerShare')?.value)).toBe(1.2);
    });

    it('提交修改后的当前持仓成本价', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds?editId=1']}><App><FundsPage/></App></MemoryRouter>,
        ));
        await act(async () => new Promise((resolve) => window.setTimeout(resolve, 0)));

        const input = document.querySelector('#costPerShare');
        await act(async () => {
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, '1.25');
            input.dispatchEvent(new Event('input', {bubbles: true}));
            document.querySelector('.ant-modal-footer .ant-btn-primary').click();
            await new Promise((resolve) => window.setTimeout(resolve, 0));
        });

        expect(updateCostBasis).toHaveBeenCalledWith({
            portfolioFundId: 11,
            body: {costPerShare: 1.25},
        });
    });

    it('编辑配置时按 portfolioFundId 更新提醒和分组', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds?editId=1']}><App><FundsPage/></App></MemoryRouter>,
        ));
        await act(async () => new Promise((resolve) => window.setTimeout(resolve, 0)));
        await act(async () => {
            document.querySelector('.ant-modal-footer .ant-btn-primary').click();
            await new Promise((resolve) => window.setTimeout(resolve, 0));
        });

        expect(updateWarning).toHaveBeenCalledWith({
            portfolioFundId: 11,
            body: {enabled: true, ratio: 0.3},
        });
        expect(replaceGroups).toHaveBeenCalledWith({
            portfolioFundId: 11,
            body: {groupNames: []},
        });
    });

    it('成本价未变化时不提交成本修正', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds?editId=1']}><App><FundsPage/></App></MemoryRouter>,
        ));
        await act(async () => new Promise((resolve) => window.setTimeout(resolve, 0)));
        await act(async () => {
            document.querySelector('.ant-modal-footer .ant-btn-primary').click();
            await new Promise((resolve) => window.setTimeout(resolve, 0));
        });

        expect(updateCostBasis).not.toHaveBeenCalled();
    });

    it('空仓基金编辑时不显示成本价输入框', async () => {
        state.funds = [{...state.funds[0], holdingShares: null, costPerShare: null}];
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds?editId=1']}><App><FundsPage/></App></MemoryRouter>,
        ));
        await act(async () => new Promise((resolve) => window.setTimeout(resolve, 0)));

        expect(document.querySelector('#costPerShare')).toBeNull();
    });
});
