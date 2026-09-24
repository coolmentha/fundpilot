import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const mocks = vi.hoisted(() => {
    const create = vi.fn();
    const update = vi.fn();
    const action = vi.fn();
    const remove = vi.fn();
    return {
        create, update, action, remove,
        plans: vi.fn(), active: vi.fn(),
        createHook: vi.fn(() => ({mutateAsync: create, isPending: false})),
        updateHook: vi.fn(() => ({mutateAsync: update, isPending: false})),
        actionHook: vi.fn(() => ({mutateAsync: action, isPending: false})),
        deleteHook: vi.fn(() => ({mutateAsync: remove, isPending: false})),
    };
});

vi.mock('../api/hooks.js', () => ({
    useDcaPlans: mocks.plans,
    useActiveDcaPlan: mocks.active,
    useCreateDcaPlan: mocks.createHook,
    useUpdateDcaPlan: mocks.updateHook,
    useDcaPlanAction: mocks.actionHook,
    useDeleteDcaPlan: mocks.deleteHook,
}));
vi.mock('./DcaPlanFormModal.jsx', () => ({
    default: ({open, editing, onOk}) => (open ? (
        <div className="dca-modal-stub">
            <span>{editing ? `edit-${editing.id}` : 'create'}</span>
            <button type="button" onClick={() => onOk({amount: 2000})}>提交计划</button>
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

const {default: FundDcaTab} = await import('./FundDcaTab.jsx');

const plans = [
    {
        id: 1, status: 'EFFECTIVE', enabled: true, frequency: 'WEEKLY', dayOfWeek: 3, amount: 1000,
        amountStrategy: 'FIXED', minimumAmount: 500, maximumAmount: 1500, createdDate: '2026-09-01T00:00:00Z',
        latestDecision: {result: 'SKIPPED', reasonCode: 'VALUATION_NOT_LOW', ruleVersion: 'v2',
            dataDate: '2026-09-20T00:00:00Z', deductionRate: 0.5, actualAmount: 0},
    },
    {
        id: 2, status: 'DRAFT', enabled: false, frequency: 'DAILY', amount: 800,
        amountStrategy: 'MOVING_AVERAGE', createdDate: '2026-09-05T00:00:00Z', latestDecision: null,
    },
];
const active = {status: 'EFFECTIVE', frequency: 'DAILY', amount: 1000, amountStrategy: 'FIXED'};

const flush = () => new Promise((resolve) => window.setTimeout(resolve, 0));
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

describe('FundDcaTab', () => {
    let container;
    let root;

    beforeEach(() => {
        mocks.create.mockReset().mockResolvedValue(undefined);
        mocks.update.mockReset().mockResolvedValue(undefined);
        mocks.action.mockReset().mockResolvedValue(undefined);
        mocks.remove.mockReset().mockResolvedValue(undefined);
        mocks.plans.mockReturnValue({data: plans, isLoading: false});
        mocks.active.mockReturnValue({data: active});
    });

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    const render = async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<App><FundDcaTab portfolioFundId={101} benchmarkIndexCode="000300.SH"/></App>));
    };

    const rows = () => [...container.querySelectorAll('.ant-table-tbody tr.ant-table-row')];

    it('展示生效计划摘要与计划列表，并按状态提供不同操作', async () => {
        await render();

        expect(container.textContent).toContain('当前生效定投计划');
        expect(container.textContent).toContain('每个交易日');
        expect(rows()).toHaveLength(2);
        expect(container.textContent).toContain('周三');
        expect(container.textContent).toContain('固定金额');
        expect(container.textContent).toContain('1,000');
        expect(container.textContent).toContain('500');
        expect(container.textContent).toContain('1,500');
        expect(container.textContent).toContain('本期跳过');
        expect(container.textContent).toContain('指数估值未处于低估区');
        expect(container.textContent).toContain('扣款率 50.00%');
        expect(container.textContent).toContain('均线策略');
        expect(container.textContent).toContain('开');
        expect(container.textContent).toContain('关');

        expect([...rows()[0].querySelectorAll('button')].map(label)).toEqual(['编辑', '暂停', '停用']);
        expect([...rows()[1].querySelectorAll('button')].map(label)).toEqual(['编辑', '激活', '删除']);

        expect(mocks.plans).toHaveBeenCalledWith(101);
        expect(mocks.active).toHaveBeenCalledWith(101);
        expect(mocks.createHook).toHaveBeenCalledWith(101);
    });

    it('暂停生效计划与删除草稿计划都经二次确认', async () => {
        await render();

        await click(buttonByText(rows()[0], '暂停'));
        await confirmPopconfirm();
        expect(mocks.action).toHaveBeenCalledWith({id: 1, action: 'pause'});

        await click(buttonByText(rows()[1], '删除'));
        await confirmPopconfirm();
        expect(mocks.remove).toHaveBeenCalledWith(2);
    });

    it('新建与编辑计划分别调用创建和更新接口', async () => {
        await render();

        await click(buttonByText(container, '新建定投计划'));
        expect(container.textContent).toContain('create');
        await click(buttonByText(container.querySelector('.dca-modal-stub'), '提交计划'));
        expect(mocks.create).toHaveBeenCalledWith({amount: 2000});
        expect(container.querySelector('.dca-modal-stub')).toBeNull();

        await click(buttonByText(rows()[0], '编辑'));
        expect(container.textContent).toContain('edit-1');
        await click(buttonByText(container.querySelector('.dca-modal-stub'), '提交计划'));
        expect(mocks.update).toHaveBeenCalledWith({id: 1, body: {amount: 2000}});
    });

    it('没有生效计划与计划数据时退化为空列表', async () => {
        mocks.active.mockReturnValue({data: null});
        mocks.plans.mockReturnValue({data: [], isLoading: false});
        await render();

        expect(container.textContent).not.toContain('当前生效定投计划');
        expect(rows()).toHaveLength(0);
        expect(buttonByText(container, '新建定投计划')).toBeDefined();
    });
});