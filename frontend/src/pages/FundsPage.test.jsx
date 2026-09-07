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
    state: {funds: [], searchResults: []},
}));

vi.mock('../api/hooks.js', () => ({
    useFunds: () => ({
        data: state.funds,
        isLoading: false, refetch: vi.fn(),
    }),
    useFundGroups: () => ({data: []}),
    useDcaBudgetSummary: () => ({data: null, isLoading: false, isError: false, refetch: vi.fn()}),
    useFundSearch: () => ({data: state.searchResults, isFetching: false}),
    useCreatePortfolioFund: () => ({mutateAsync: saveFund, isPending: false}),
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
Element.prototype.scrollIntoView = vi.fn();
const {default: FundsPage} = await import('./FundsPage.jsx');

async function setInputValue(input, value) {
    await act(async () => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
        input.dispatchEvent(new Event('input', {bubbles: true}));
        input.dispatchEvent(new Event('change', {bubbles: true}));
        await Promise.resolve();
    });
}

async function click(element) {
    await act(async () => {
        element.click();
        await Promise.resolve();
    });
}

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
        state.searchResults = [];
    });

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    it('通过 editPortfolioFundId 查询参数自动打开目标基金编辑弹窗', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds?editPortfolioFundId=11']}><App><FundsPage/></App></MemoryRouter>,
        ));
        await act(async () => new Promise((resolve) => window.setTimeout(resolve, 0)));

        expect(document.body.textContent).toContain('编辑基金');
        expect(document.body.textContent).toContain('测试基金');
        expect(Number(document.querySelector('#costPerShare')?.value)).toBe(1.2);
    });

    it('基金列表以组合基金标识区分多个无旧标识记录', async () => {
        state.funds = [
            {...state.funds[0], id: null, portfolioFundId: 11},
            {...state.funds[0], id: null, portfolioFundId: 12, fundCode: '000002', fundName: '第二只基金'},
        ];
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds']}><App><FundsPage/></App></MemoryRouter>,
        ));

        expect([...container.querySelectorAll('tbody tr[data-row-key]')]
            .map((row) => row.getAttribute('data-row-key'))).toEqual(['11', '12']);
    });

    it('编辑基金只读展示纪律分类及其来源', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds?editPortfolioFundId=11']}><App><FundsPage/></App></MemoryRouter>,
        ));
        await act(async () => new Promise((resolve) => window.setTimeout(resolve, 0)));

        expect(document.body.textContent).toContain('来自纪律配置');
        expect(document.querySelector('#fundCategory')).toBeNull();
        await click(document.querySelector('.ant-modal-footer .ant-btn-primary'));

        expect(updateWarning).toHaveBeenCalledWith({
            portfolioFundId: 11,
            body: {enabled: true, ratio: 0.3},
        });
        expect(replaceGroups).toHaveBeenCalledWith({
            portfolioFundId: 11,
            body: {groupNames: []},
        });
    });

    it('提交修改后的当前持仓成本价', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds?editPortfolioFundId=11']}><App><FundsPage/></App></MemoryRouter>,
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
            <MemoryRouter initialEntries={['/funds?editPortfolioFundId=11']}><App><FundsPage/></App></MemoryRouter>,
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
            <MemoryRouter initialEntries={['/funds?editPortfolioFundId=11']}><App><FundsPage/></App></MemoryRouter>,
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
            <MemoryRouter initialEntries={['/funds?editPortfolioFundId=11']}><App><FundsPage/></App></MemoryRouter>,
        ));
        await act(async () => new Promise((resolve) => window.setTimeout(resolve, 0)));

        expect(document.querySelector('#costPerShare')).toBeNull();
    });

    it('从产品目录创建组合基金时只提交新入口契约字段', async () => {
        state.searchResults = [{
            id: 23,
            fundCode: '000023',
            fundName: '目录基金',
            productType: 'INDEX',
            defaultDisciplineCategory: 'INDEX',
            benchmarkIndexCode: '000300',
        }];
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds']}><App><FundsPage/></App></MemoryRouter>,
        ));

        await click([...container.querySelectorAll('button')]
            .find((button) => button.textContent.includes('新建基金')));
        const modal = [...document.body.querySelectorAll('.ant-modal')].at(-1);
        await setInputValue(modal.querySelector('[role="combobox"]'), '000023');
        await click([...document.body.querySelectorAll('.ant-select-item-option')]
            .find((option) => option.textContent.includes('目录基金')));
        expect(modal.textContent).toContain('来自产品目录的默认建议');
        expect(document.querySelector('#fundCategory')).toBeNull();
        await click(modal.querySelector('.ant-modal-footer .ant-btn-primary'));
        await act(async () => Promise.resolve());

        expect(saveFund).toHaveBeenCalledWith({
            fundProductId: 23,
            positionWarningEnabled: true,
            positionWarningRatio: 0.3,
            initialHoldingShares: null,
            costPerShare: null,
            openedAt: null,
            groupNames: [],
        });
    });
});
