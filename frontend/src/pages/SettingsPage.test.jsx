import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {SiteAuthContext} from '../auth/SiteAuthContext.js';

const {updateBudget, replaceWatchedIndices, updateEmail, updateUser, rotateToken, revokeToken,
    tokenState} = vi.hoisted(() => ({
    updateBudget: vi.fn(),
    replaceWatchedIndices: vi.fn(),
    updateEmail: vi.fn(),
    updateUser: vi.fn(),
    rotateToken: vi.fn(),
    revokeToken: vi.fn(),
    tokenState: {data: {active: true, rotatedAt: '2026-10-25T00:00:00Z'}, isError: false, refetch: vi.fn()},
}));

vi.mock('../api/hooks.js', () => ({
    useInvestmentPlanBudget: () => ({data: {monthlyBudget: 2500}, isLoading: false, isError: false}),
    useWatchedIndices: () => ({data: {indexCodes: ['1.000001', '1.000300']}, isLoading: false, isError: false}),
    useUpdateInvestmentPlanBudget: () => ({mutateAsync: updateBudget, isPending: false}),
    useReplaceWatchedIndices: () => ({mutateAsync: replaceWatchedIndices, isPending: false}),
    useUpdateSiteEmail: () => ({mutateAsync: updateEmail, isPending: false}),
}));
vi.mock('../api/openApiToken.js', () => ({
    useOpenApiToken: () => tokenState,
    useRotateOpenApiToken: () => ({mutateAsync: rotateToken, isPending: false}),
    useRevokeOpenApiToken: () => ({mutateAsync: revokeToken, isPending: false}),
}));
vi.mock('../components/YangjibaoImportModal.jsx', () => ({default: () => null}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}));
window.ResizeObserver = class {
    observe() {
    }

    unobserve() {
    }

    disconnect() {
    }
};

const {default: SettingsPage} = await import('./SettingsPage.jsx');

const setValue = (input, value) => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', {bubbles: true}));
};

describe('SettingsPage', () => {
    let container;
    let root;

    beforeEach(() => {
        updateBudget.mockReset().mockResolvedValue(undefined);
        replaceWatchedIndices.mockReset().mockResolvedValue(undefined);
        updateEmail.mockReset().mockResolvedValue({id: 1, username: 'alice', role: 'USER', email: 'new@example.com'});
        updateUser.mockReset();
        rotateToken.mockReset().mockResolvedValue({token: 'fp_new_token'});
        revokeToken.mockReset().mockResolvedValue(true);
        tokenState.data = {active: true, rotatedAt: '2026-10-25T00:00:00Z'};
        tokenState.isError = false;
        tokenState.refetch = vi.fn();
        Object.defineProperty(navigator, 'clipboard', {
            configurable: true,
            value: {writeText: vi.fn().mockResolvedValue(undefined)},
        });
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
        await act(async () => root.render(
            <App>
                <SiteAuthContext.Provider value={{user: {id: 1, username: 'alice', email: 'old@example.com'}, updateUser}}>
                    <SettingsPage/>
                </SiteAuthContext.Provider>
            </App>,
        ));
    };

    it('保存当前月度预算和关注指数到对应写接口', async () => {
        await render();

        const buttons = [...container.querySelectorAll('button')];
        await act(async () => buttons.find(button => button.textContent.includes('保存月度预算')).click());
        await act(async () => buttons.find(button => button.textContent.includes('保存关注指数')).click());

        expect(updateBudget).toHaveBeenCalledWith(2500);
        expect(replaceWatchedIndices).toHaveBeenCalledWith(['1.000001', '1.000300']);
    });

    it('保存提醒邮箱并刷新当前用户', async () => {
        await render();

        const input = container.querySelector('input[aria-label="提醒邮箱"]');
        expect(input.value).toBe('old@example.com');
        await act(async () => setValue(input, 'new@example.com'));
        await act(async () => [...container.querySelectorAll('button')]
            .find(button => button.textContent.includes('保存提醒邮箱')).click());

        expect(updateEmail).toHaveBeenCalledWith('new@example.com');
        expect(updateUser).toHaveBeenCalledWith({id: 1, username: 'alice', role: 'USER', email: 'new@example.com'});
    });

    it('刷新 Token 后只显示新明文，撤销后清空显示', async () => {
        await render();

        const buttons = [...container.querySelectorAll('button')];
        await act(async () => buttons.find(button => button.textContent.includes('刷新 Token')).click());

        expect(rotateToken).toHaveBeenCalledTimes(1);
        expect(container.querySelector('textarea[aria-label="新 Open API Token"]').value).toBe('fp_new_token');

        const revoke = [...container.querySelectorAll('button')]
            .find(button => button.textContent.includes('撤销 Token'));
        await act(async () => revoke.click());

        expect(revokeToken).toHaveBeenCalledTimes(1);
        expect(container.querySelector('textarea[aria-label="新 Open API Token"]')).toBeNull();
    });

    it('首次创建 Token 并复制明文', async () => {
        tokenState.data = {active: false};
        await render();
        expect(container.textContent).toContain('尚未创建 Open API Token');

        const create = [...container.querySelectorAll('button')]
            .find(button => button.textContent.includes('创建 Token'));
        await act(async () => create.click());
        expect(rotateToken).toHaveBeenCalledTimes(1);

        const copy = [...container.querySelectorAll('button')]
            .find(button => button.textContent.includes('复制 Token'));
        await act(async () => copy.click());
        expect(navigator.clipboard.writeText).toHaveBeenCalledWith('fp_new_token');

        const dismiss = [...container.querySelectorAll('button')]
            .find(button => button.textContent.includes('我已知晓'));
        await act(async () => dismiss.click());
        expect(container.querySelector('textarea[aria-label="新 Open API Token"]')).toBeNull();
    });

    it('Token 状态加载失败时可重试', async () => {
        tokenState.isError = true;
        await render();

        expect(container.textContent).toContain('Open API Token 状态加载失败');
        await act(async () => [...container.querySelectorAll('button')]
            .find(button => button.textContent.includes('重试')).click());
        expect(tokenState.refetch).toHaveBeenCalledTimes(1);
    });
});
