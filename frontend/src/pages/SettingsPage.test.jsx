import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {SiteAuthContext} from '../auth/SiteAuthContext.js';

const {updateBudget, replaceWatchedIndices, updateEmail, updateUser} = vi.hoisted(() => ({
    updateBudget: vi.fn(),
    replaceWatchedIndices: vi.fn(),
    updateEmail: vi.fn(),
    updateUser: vi.fn(),
}));

vi.mock('../api/hooks.js', () => ({
    useInvestmentPlanBudget: () => ({data: {monthlyBudget: 2500}, isLoading: false, isError: false}),
    useWatchedIndices: () => ({data: {indexCodes: ['1.000001', '1.000300']}, isLoading: false, isError: false}),
    useUpdateInvestmentPlanBudget: () => ({mutateAsync: updateBudget, isPending: false}),
    useReplaceWatchedIndices: () => ({mutateAsync: replaceWatchedIndices, isPending: false}),
    useUpdateSiteEmail: () => ({mutateAsync: updateEmail, isPending: false}),
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
});
