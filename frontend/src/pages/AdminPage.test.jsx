import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const mocks = vi.hoisted(() => ({
    adminAction: vi.fn(),
    userMutate: vi.fn(),
    userMutateAsync: vi.fn(),
    refetch: vi.fn(),
    adminUsers: vi.fn(),
    jobStatus: vi.fn(),
}));

vi.mock('../api/hooks.js', () => ({
    useAdminAction: () => ({mutateAsync: mocks.adminAction, isPending: false}),
    useAdminUsers: mocks.adminUsers,
    useAdminJobStatus: mocks.jobStatus,
    useAdminUserMutation: () => ({mutate: mocks.userMutate, mutateAsync: mocks.userMutateAsync, isPending: false}),
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

const {default: AdminPage} = await import('./AdminPage.jsx');

const users = [
    {id: 1, username: 'alice', role: 'ADMIN', enabled: true},
    {id: 2, username: 'bob', role: 'USER', enabled: false},
];
const jobs = [
    {task: 'suggestionJob', lastFinishedAt: '2026-09-18T06:30:00Z', lastDurationMillis: 1500,
        lastResult: 'SUCCESS', consecutiveFailures: 0, lastFailureMessage: null},
    {task: 'navConfirmJob', lastFinishedAt: '2026-09-17T06:30:00Z', lastDurationMillis: 820,
        lastResult: 'FAILURE', consecutiveFailures: 3, lastFailureMessage: '最新净值缺失'},
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

async function setInputValue(input, value) {
    await act(async () => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
        input.dispatchEvent(new Event('input', {bubbles: true}));
        await flush();
    });
}

async function openTab(container, name) {
    await click([...container.querySelectorAll('.ant-tabs-tab-btn')].find((tab) => tab.textContent === name));
}

async function confirmPopconfirm() {
    const buttons = [...document.querySelectorAll('.ant-popconfirm-buttons')];
    await click(buttons.at(-1).querySelector('.ant-btn-primary'));
}

describe('AdminPage', () => {
    let container;
    let root;

    beforeEach(() => {
        mocks.adminAction.mockReset().mockResolvedValue(undefined);
        mocks.userMutate.mockReset().mockResolvedValue(undefined);
        mocks.userMutateAsync.mockReset().mockResolvedValue(undefined);
        mocks.refetch.mockReset();
        mocks.adminUsers.mockReturnValue({data: users, isLoading: false});
        mocks.jobStatus.mockReturnValue({data: jobs, isLoading: false, isFetching: false, refetch: mocks.refetch});
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
        await act(async () => root.render(<App><AdminPage/></App>));
    };

    const activePane = () => container.querySelector('.ant-tabs-tabpane-active');

    it('管理操作默认页在确认框确认后触发对应后台动作', async () => {
        await render();

        expect(activePane().textContent).toContain('管理操作');
        expect(activePane().textContent).toContain('同步交易日历');

        await click(buttonByText(activePane(), '回填净值'));
        await confirmPopconfirm();
        expect(mocks.adminAction).toHaveBeenCalledWith({action: 'confirm-nav'});

        await click(buttonByText(activePane(), '同步交易日历'));
        await confirmPopconfirm();
        expect(mocks.adminAction).toHaveBeenCalledWith({action: 'sync-calendar'});
        expect(mocks.adminAction).toHaveBeenCalledTimes(2);
    });

    it('系统监控统计任务状态并支持按任务名搜索与刷新', async () => {
        await render();
        await openTab(container, '系统监控');

        const pane = activePane();
        expect(pane.textContent).toContain('共 2 个任务 · 1 个连续失败');
        expect(pane.textContent).toContain('suggestionJob');
        expect(pane.textContent).toContain('navConfirmJob');
        expect(pane.textContent).toContain('1.5s');
        expect(pane.textContent).toContain('820ms');
        expect(pane.textContent).toContain('成功');
        expect(pane.textContent).toContain('失败');
        expect(pane.textContent).toContain('3 次');
        expect(pane.textContent).toContain('最新净值缺失');
        expect(pane.querySelectorAll('.ant-table-tbody tr.ant-table-row')).toHaveLength(2);

        await setInputValue(pane.querySelector('input[aria-label="按任务名搜索"]'), 'navConfirm');
        const filtered = [...pane.querySelectorAll('.ant-table-tbody tr.ant-table-row')];
        expect(filtered).toHaveLength(1);
        expect(filtered[0].textContent).toContain('navConfirmJob');

        await click(buttonByText(pane, '刷新'));
        expect(mocks.refetch).toHaveBeenCalled();
    });

    it('用户管理展示统计，可切换启用状态并新建用户', async () => {
        await render();
        await openTab(container, '用户管理');

        const pane = activePane();
        expect(pane.textContent).toContain('共 2 位用户 · 1 位启用 · 1 位管理员');
        expect(pane.textContent).toContain('管理员');
        expect(pane.textContent).toContain('普通用户');
        expect(pane.textContent).toContain('已启用');
        expect(pane.textContent).toContain('已停用');

        await click(pane.querySelector('[aria-label="启用 bob"]'));
        await confirmPopconfirm();
        expect(mocks.userMutate).toHaveBeenCalledWith({
            path: '/api/admin/users/2/status', body: {enabled: true},
        });

        await click(buttonByText(pane, '新建用户'));
        await setInputValue(document.querySelector('#username'), 'carol');
        await setInputValue(document.querySelector('#password'), 'secret');
        await click(document.querySelector('.ant-modal-footer .ant-btn-primary'));

        expect(mocks.userMutateAsync).toHaveBeenCalledWith({
            path: '/api/admin/users',
            body: {username: 'carol', password: 'secret', role: 'USER'},
        });
    });

    it('没有任务记录时系统监控展示空态', async () => {
        mocks.jobStatus.mockReturnValue({data: [], isLoading: false, isFetching: false, refetch: mocks.refetch});
        await render();
        await openTab(container, '系统监控');

        expect(activePane().textContent).toContain('共 0 个任务');
        expect(activePane().textContent).toContain('暂无任务执行记录');
    });
});