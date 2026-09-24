import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const mocks = vi.hoisted(() => {
    const update = vi.fn();
    const action = vi.fn();
    const remove = vi.fn();
    return {
        update, action, remove,
        plans: vi.fn(), funds: vi.fn(), budget: vi.fn(),
        updateHook: vi.fn(() => ({mutateAsync: update, isPending: false})),
        actionHook: vi.fn(() => ({mutateAsync: action, isPending: false})),
        deleteHook: vi.fn(() => ({mutateAsync: remove, isPending: false})),
    };
});

vi.mock('../api/hooks.js', () => ({
    useDcaManagementPlans: mocks.plans,
    useFunds: mocks.funds,
    useDcaBudgetSummary: mocks.budget,
    useUpdateDcaPlan: mocks.updateHook,
    useDcaPlanAction: mocks.actionHook,
    useDeleteDcaPlan: mocks.deleteHook,
}));
vi.mock('../components/DcaBudgetOverview.jsx', () => ({
    default: ({summary, isLoading}) => (
        <div className="budget-stub">预算总览 {isLoading ? '加载中' : (summary ? summary.monthlyBudget : '无')}</div>
    ),
}));
vi.mock('./DcaPlanFormModal.jsx', () => ({
    default: ({open, editing, onOk}) => (open ? (
        <div className="dca-modal-stub">
            <span>{editing ? `edit-${editing.id}` : 'create'}</span>
            <button type="button" onClick={() => onOk({amount: 1200})}>提交计划</button>
        </div>
    ) : null),
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
// 桌面断点全部命中,保证 responsive 列参与渲染。
window.matchMedia = (query) => ({
    matches: !String(query).includes('max-width'),
    media: String(query),
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
});
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

const {default: DcaManagementPage} = await import('./DcaManagementPage.jsx');

const funds = [{portfolioFundId: 41, fundName: '招商中证白酒', fundCode: '161725'}];
const plans = [
    {
        id: 1, portfolioFundId: 41, status: 'EFFECTIVE', enabled: true, amount: 1000, frequency: 'WEEKLY',
        dayOfWeek: 3, amountStrategy: 'FIXED', remainingAmount: 500, remainingOccurrences: 2,
        remainingExecutionDates: ['2026-09-25T00:00:00Z'],
        latestDecision: {result: 'SKIPPED', reasonCode: 'VALUATION_NOT_LOW', ruleVersion: 'v2',
            dataDate: '2026-09-20T00:00:00Z', deductionRate: 0.5, actualAmount: 0},
    },
    {
        id: 2, portfolioFundId: 41, status: 'DRAFT', enabled: false, amount: 800, frequency: 'DAILY',
        amountStrategy: 'FIXED', remainingAmount: 800, remainingOccurrences: 1,
        remainingExecutionDates: [], latestDecision: null,
    },
];

const flush = () => new Promise((resolve) => window.setTimeout(resolve, 0));
const label = (element) => element.textContent.replace(/\s/g, '');
const buttonByText = (scope, text) => [...scope.querySelectorAll('button')].find((button) => label(button) === text);

async function click(element) {
    await act(async () => {
        element.click();
        await flush();
    });
}

async function confirmDialog() {
    const confirmations = [...document.querySelectorAll('.ant-modal-confirm-btns')];
    await click(confirmations.at(-1).querySelector('.ant-btn-primary'));
}

describe('DcaManagementPage', () => {
    let container;
    let root;

    beforeEach(() => {
        mocks.update.mockReset().mockResolvedValue(undefined);
        mocks.action.mockReset().mockResolvedValue(undefined);
        mocks.remove.mockReset().mockResolvedValue(undefined);
        mocks.plans.mockReturnValue({data: plans, isLoading: false, isError: false, refetch: vi.fn()});
        mocks.funds.mockReturnValue({data: funds});
        mocks.budget.mockReturnValue({data: {monthlyBudget: 2500}, isLoading: false, isError: false, refetch: vi.fn()});
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
        await act(async () => root.render(<MemoryRouter><App><DcaManagementPage/></App></MemoryRouter>));
    };

    const rows = () => [...container.querySelectorAll('.ant-table-tbody tr.ant-table-row')];
    // 旧的下拉菜单关闭后仍留在 DOM,只取最新打开的一个。
    const menuItems = () => {
        const menu = [...document.querySelectorAll('.ant-dropdown-menu')].at(-1);
        return menu ? [...menu.querySelectorAll('.ant-dropdown-menu-item')] : [];
    };
    const menuItemByText = (text) => menuItems().find((item) => label(item) === text);

    it('把基金信息合并进计划并渲染状态、决策与预算总览', async () => {
        await render();

        expect(container.textContent).toContain('2 个计划');
        expect(container.textContent).toContain('预算总览 2500');
        expect(rows()).toHaveLength(2);

        const fundLink = [...container.querySelectorAll('a')].find((link) => link.textContent === '招商中证白酒');
        expect(fundLink.getAttribute('href')).toBe('/funds/41');
        expect(container.textContent).toContain('161725');
        expect(container.textContent).toContain('运行中');
        expect(container.textContent).toContain('已停用');

        expect(container.textContent).toContain('周三');
        expect(container.textContent).toContain('固定金额');
        expect(container.textContent).toContain('本期跳过');
        expect(container.textContent).toContain('指数估值未处于低估区');
        expect(container.textContent).toContain('扣款率 50.00%');
        expect(container.textContent).toContain('500');
        expect(container.textContent).toContain('2 次');
        expect(container.textContent).toContain('2026-09-25');

        expect(rows()[0].querySelector('button[aria-label="编辑计划"]')).not.toBeNull();
        expect(rows()[1].querySelector('button[aria-label="更多计划操作"]')).not.toBeNull();
    });

    it('按计划状态给出不同菜单项并二次确认后调用对应接口', async () => {
        await render();

        await click(rows()[1].querySelector('button[aria-label="更多计划操作"]'));
        expect(menuItems().map(label)).toEqual(['激活', '删除']);
        await click(menuItemByText('激活'));
        await confirmDialog();
        expect(mocks.action).toHaveBeenCalledWith({id: 2, action: 'activate'});

        await click(rows()[0].querySelector('button[aria-label="更多计划操作"]'));
        expect(menuItems().map(label)).toEqual(['暂停', '停用']);
        await click(menuItemByText('停用'));
        await confirmDialog();
        expect(mocks.action).toHaveBeenCalledWith({id: 1, action: 'retire'});
    });

    it('编辑计划时回填并提交更新', async () => {
        await render();

        await click(rows()[0].querySelector('button[aria-label="编辑计划"]'));
        expect(container.textContent).toContain('edit-1');
        await click(buttonByText(container.querySelector('.dca-modal-stub'), '提交计划'));

        expect(mocks.update).toHaveBeenCalledWith({id: 1, body: {amount: 1200}});
        expect(container.querySelector('.dca-modal-stub')).toBeNull();
    });

    it('计划加载失败展示错误态并可重试，无计划时展示空态', async () => {
        const refetch = vi.fn();
        mocks.plans.mockReturnValue({data: undefined, isLoading: false, isError: true, refetch});
        await render();

        expect(container.textContent).toContain('定投计划加载失败');
        await click(buttonByText(container, '重试'));
        expect(refetch).toHaveBeenCalled();

        mocks.plans.mockReturnValue({data: [], isLoading: false, isError: false, refetch: vi.fn()});
        await act(async () => root.unmount());
        container.remove();
        await render();

        expect(container.textContent).toContain('暂无定投计划');
        expect(rows()).toHaveLength(0);
    });
});