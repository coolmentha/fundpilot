import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';

const signal = {
    id: 11,
    fundId: 1,
    signalType: 'SELL',
    reason: 'LOGIC_BROKEN',
    actionStatus: 'PENDING',
    suggestedMeasure: {value: 1, measureUnit: 'SHARE'},
};

vi.mock('../api/hooks.js', () => ({
    useFunds: () => ({
        data: [{id: 1, fundCode: '000001', fundName: '测试基金', holdingShares: 100}],
        isLoading: false,
        isError: false,
    }),
    usePendingSignals: () => ({data: [signal], isLoading: false, isError: false, refetch: vi.fn()}),
    useSignalsToday: () => ({data: null, isLoading: false, isError: false, refetch: vi.fn()}),
    useSignalsRange: () => ({data: [], isLoading: false, isError: false, refetch: vi.fn()}),
    useConfirmOperation: () => ({mutateAsync: vi.fn(), isPending: false}),
    useIgnoreSignal: () => ({mutateAsync: vi.fn()}),
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
window.getComputedStyle = () => ({width: '0px'});
const {default: SignalsPage} = await import('./SignalsPage.jsx');

describe('SignalsPage', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
    });

    it('逻辑止损固定为全仓份额且禁止编辑', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(
            <MemoryRouter><App><SignalsPage/></App></MemoryRouter>,
        ));
        const acceptButton = [...document.querySelectorAll('button')]
            .find((button) => button.textContent === '采纳');
        await act(async () => acceptButton.click());

        const sharesInput = document.querySelector('.ant-modal input[role="spinbutton"]');
        expect(document.body.textContent).toContain('逻辑止损卖出份额（全仓，当前持仓 100.00 份）');
        expect(Number(sharesInput?.value)).toBe(100);
        expect(sharesInput?.disabled).toBe(true);
    });
});
