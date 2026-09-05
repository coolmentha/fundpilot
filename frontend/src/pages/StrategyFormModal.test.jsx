import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, describe, expect, it, vi} from 'vitest';

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

const {default: StrategyFormModal} = await import('./StrategyFormModal.jsx');

const recommendation = {
    fundCategory: 'BROAD_BASE',
    presetVersion: 1,
    profitActivationPercent: 0.15,
    stopLossPullbackPercent: 0.06,
    profitHarvestPercent: 0.5,
    minimumHoldingPercent: 0.5,
    maxSingleSellPercent: 0.2,
    cooldownTradingDays: 10,
};

describe('StrategyFormModal', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    it.each([
        ['创建', null],
        ['更新', {...recommendation, id: 7}],
    ])('%s策略时拒绝 100%% 并接受 99.99%% 启动收益率', async (_name, editing) => {
        const onOk = vi.fn();
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <StrategyFormModal open editing={editing} recommendation={recommendation}
                               onOk={onOk} onCancel={vi.fn()} confirmLoading={false}/>,
        ));
        await act(async () => new Promise((resolve) => window.setTimeout(resolve, 0)));

        const activation = document.querySelector('#profitActivationPercent');
        await setInputValue(activation, '100');
        await clickConfirm();

        expect(onOk).not.toHaveBeenCalled();

        await setInputValue(activation, '99.99');
        await clickConfirm();

        expect(onOk).toHaveBeenCalledWith(expect.objectContaining({
            profitActivationPercent: 0.9999,
            presetFundCategory: 'BROAD_BASE',
            presetVersion: 1,
            customized: true,
        }));
    });
});

async function setInputValue(input, value) {
    await act(async () => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
        input.dispatchEvent(new Event('input', {bubbles: true}));
        input.dispatchEvent(new Event('change', {bubbles: true}));
        await new Promise((resolve) => window.setTimeout(resolve, 0));
    });
}

async function clickConfirm() {
    await act(async () => {
        document.querySelector('.ant-modal-footer .ant-btn-primary').click();
        await new Promise((resolve) => window.setTimeout(resolve, 0));
    });
}
