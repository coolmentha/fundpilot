import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('../api/hooks.js', () => ({
    useFunds: () => ({data: [{id: 1, fundName: '目标基金'}, {id: 2, fundName: '其他基金'}]}),
    usePendingTransactions: () => ({
        data: [{id: 11, fundId: 1, status: 'PENDING'}, {id: 12, fundId: 2, status: 'PENDING'}],
        isLoading: false, isError: false, refetch: vi.fn(),
    }),
    useConfirmTransaction: () => ({mutate: vi.fn(), isPending: false}),
    useCancelTransaction: () => ({mutate: vi.fn(), isPending: false}),
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
const {default: ConfirmPage} = await import('./ConfirmPage.jsx');

describe('ConfirmPage', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        root = null;
        container = null;
    });

    it('按 fundId 查询参数筛选待确认交易', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/confirm?fundId=1']}><App><ConfirmPage/></App></MemoryRouter>,
        ));

        expect(container.textContent).toContain('仅显示 目标基金 的待净值确认交易。');
        expect(container.textContent).toContain('目标基金');
        expect(container.textContent).not.toContain('其他基金');
    });
});
