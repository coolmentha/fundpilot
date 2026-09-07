import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const {updateBudget, replaceWatchedIndices} = vi.hoisted(() => ({
    updateBudget: vi.fn(),
    replaceWatchedIndices: vi.fn(),
}));

vi.mock('../api/hooks.js', () => ({
    useInvestmentPlanBudget: () => ({data: {monthlyBudget: 2500}, isLoading: false, isError: false}),
    useWatchedIndices: () => ({data: {indexCodes: ['1.000001', '1.000300']}, isLoading: false, isError: false}),
    useUpdateInvestmentPlanBudget: () => ({mutateAsync: updateBudget, isPending: false}),
    useReplaceWatchedIndices: () => ({mutateAsync: replaceWatchedIndices, isPending: false}),
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
    observe() {}
    unobserve() {}
    disconnect() {}
};

const {default: SettingsPage} = await import('./SettingsPage.jsx');

describe('SettingsPage', () => {
    let container;
    let root;

    beforeEach(() => {
        updateBudget.mockReset().mockResolvedValue(undefined);
        replaceWatchedIndices.mockReset().mockResolvedValue(undefined);
    });

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    it('保存当前月度预算和关注指数到对应写接口', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<App><SettingsPage/></App>));

        const buttons = [...container.querySelectorAll('button')];
        await act(async () => buttons.find(button => button.textContent.includes('保存月度预算')).click());
        await act(async () => buttons.find(button => button.textContent.includes('保存关注指数')).click());

        expect(updateBudget).toHaveBeenCalledWith(2500);
        expect(replaceWatchedIndices).toHaveBeenCalledWith(['1.000001', '1.000300']);
    });
});
