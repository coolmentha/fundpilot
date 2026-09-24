import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const mocks = vi.hoisted(() => {
    const create = vi.fn();
    const update = vi.fn();
    const action = vi.fn();
    return {
        create, update, action,
        createHook: vi.fn(() => ({mutateAsync: create, isPending: false})),
        updateHook: vi.fn(() => ({mutateAsync: update, isPending: false})),
        actionHook: vi.fn(() => ({mutateAsync: action, isPending: false})),
        strategies: vi.fn(), active: vi.fn(), recommendation: vi.fn(),
    };
});

vi.mock('../api/hooks.js', () => ({
    useStrategies: mocks.strategies,
    useActiveStrategy: mocks.active,
    useStrategyRecommendation: mocks.recommendation,
    useCreateStrategy: mocks.createHook,
    useUpdateStrategy: mocks.updateHook,
    useStrategyAction: mocks.actionHook,
}));
vi.mock('./StrategyFormModal.jsx', () => ({
    default: ({open, editing, recommendation, onOk}) => (open ? (
        <div className="strategy-modal-stub">
            <span>{editing ? `edit-${editing.id}` : 'create'}</span>
            <span>推荐-{recommendation?.fundCategory}</span>
            <button type="button" onClick={() => onOk({profitActivationPercent: 0.2})}>提交策略</button>
        </div>
    ) : null),
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}));
globalThis.ResizeObserver = class {
    observe() {
    }

    unobserve() {
    }

    disconnect() {
    }
};
const originalGetComputedStyle = window.getComputedStyle;
window.getComputedStyle = (element) => originalGetComputedStyle.call(window, element);

const {default: FundStrategyTab} = await import('./FundStrategyTab.jsx');

const active = {
    id: 3, status: 'EFFECTIVE', takeProfitPhase: 'ARMED', customized: true,
    profitActivationPercent: 0.15, stopLossPullbackPercent: 0.06, profitHarvestPercent: 0.5,
    minimumHoldingPercent: 0.5, maxSingleSellPercent: 0.2, cooldownTradingDays: 10,
};
const strategies = [
    {id: 1, status: 'DRAFT', profitActivationPercent: 0.1, stopLossPullbackPercent: 0.05,
        profitHarvestPercent: 0.4, customized: false},
    {id: 2, status: 'EFFECTIVE', profitActivationPercent: 0.2, stopLossPullbackPercent: 0.07,
        profitHarvestPercent: 0.6, customized: true},
];
const recommendation = {
    fundCategory: 'BROAD_BASE', presetVersion: 1, profitActivationPercent: 0.15,
    stopLossPullbackPercent: 0.06, profitHarvestPercent: 0.5, minimumHoldingPercent: 0.5,
    maxSingleSellPercent: 0.2, cooldownTradingDays: 10,
};

const flush = () => new Promise((resolve) => window.setTimeout(resolve, 0));
// antd 会在两个汉字的按钮文案间插入空格,比较前统一去除空白。
const label = (element) => element.textContent.replace(/\s/g, '');
const buttonByText = (scope, text) => [...scope.querySelectorAll('button')].find((button) => label(button) === text);

async function click(element) {
    await act(async () => {
        element.click();
        await flush();
    });
}

async function confirmPopconfirm() {
    const ok = [...document.querySelectorAll('.ant-popconfirm-buttons')]
        .at(-1).querySelector('.ant-btn-primary');
    await click(ok);
}

describe('FundStrategyTab', () => {
    let container;
    let root;

    beforeEach(() => {
        mocks.create.mockReset().mockResolvedValue(undefined);
        mocks.update.mockReset().mockResolvedValue(undefined);
        mocks.action.mockReset().mockResolvedValue(undefined);
        mocks.strategies.mockReturnValue({data: strategies, isLoading: false});
        mocks.active.mockReturnValue({data: active});
        mocks.recommendation.mockReturnValue({data: recommendation, isLoading: false});
    });

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    const render = async () => {
        if (root) {
            await act(async () => root.unmount());
            container?.remove();
            document.body.innerHTML = '';
        }
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<App><FundStrategyTab portfolioFundId={11}/></App>));
    };

    const rows = () => [...container.querySelectorAll('.ant-table-tbody tr.ant-table-row')];

    it('展示当前生效策略详情与策略列表', async () => {
        await render();

        expect(container.textContent).toContain('当前生效策略');
        expect(container.textContent).toContain('已启动');
        expect(container.textContent).toContain('15.00%');
        expect(container.textContent).toContain('最低保留');
        expect(container.textContent).toContain('50.00%');
        expect(container.textContent).toContain('冷静期');
        expect(container.textContent).toContain('10 个交易日');

        expect(rows()).toHaveLength(2);
        expect(container.textContent).toContain('10.00%');
        expect(container.textContent).toContain('40.00%');
        expect(container.textContent).toContain('类型推荐');
        expect(mocks.createHook).toHaveBeenCalledWith(11);
        expect(mocks.strategies).toHaveBeenCalledWith(11);
    });

    it('非生效策略可激活、生效策略可停用', async () => {
        await render();

        const draftRow = rows()[0];
        expect([...draftRow.querySelectorAll('button')].map(label)).toEqual(['编辑', '激活']);
        const effectiveRow = rows()[1];
        expect([...effectiveRow.querySelectorAll('button')].map(label)).toEqual(['停用']);

        await click(buttonByText(draftRow, '激活'));
        await confirmPopconfirm();
        expect(mocks.action).toHaveBeenCalledWith({id: 1, action: 'activate'});

        await click(buttonByText(effectiveRow, '停用'));
        await confirmPopconfirm();
        expect(mocks.action).toHaveBeenCalledWith({id: 2, action: 'retire'});
    });

    it('缺少推荐参数时禁用新建策略', async () => {
        mocks.recommendation.mockReturnValue({data: null, isLoading: false});
        await render();

        expect(buttonByText(container, '新建策略').disabled).toBe(true);
    });

    it('编辑与新建分别调用更新和创建，并携带推荐参数', async () => {
        await render();

        await click(buttonByText(rows()[0], '编辑'));
        expect(container.textContent).toContain('edit-1');
        await click(buttonByText(container.querySelector('.strategy-modal-stub'), '提交策略'));
        expect(mocks.update).toHaveBeenCalledWith({id: 1, body: {profitActivationPercent: 0.2}});
        expect(container.textContent).not.toContain('edit-1');

        await click(buttonByText(container, '新建策略'));
        expect(container.textContent).toContain('create');
        expect(container.textContent).toContain('推荐-BROAD_BASE');
        await click(buttonByText(container.querySelector('.strategy-modal-stub'), '提交策略'));
        expect(mocks.create).toHaveBeenCalledWith({profitActivationPercent: 0.2});
    });
});