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
Element.prototype.scrollIntoView = vi.fn();

const {default: DcaPlanFormModal} = await import('./DcaPlanFormModal.jsx');

const flush = () => new Promise((resolve) => window.setTimeout(resolve, 0));

async function click(element) {
    await act(async () => {
        element.click();
        await flush();
    });
}

async function setInputValue(input, value) {
    await act(async () => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')
            .set.call(input, String(value));
        input.dispatchEvent(new Event('input', {bubbles: true}));
        input.dispatchEvent(new Event('change', {bubbles: true}));
        await flush();
    });
}

async function selectSegmented(label) {
    const item = [...document.querySelectorAll('.ant-segmented-item')]
        .find((element) => element.textContent.trim() === label);
    await click(item);
}

const formItemByLabel = (label) => [...document.querySelectorAll('.ant-form-item')]
    .find((element) => element.querySelector('.ant-form-item-label')?.textContent.trim() === label);

async function selectOption(label, optionText) {
    const select = formItemByLabel(label).querySelector('.ant-select');
    await act(async () => {
        select.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
        await flush();
    });
    const option = [...document.querySelectorAll('.ant-select-item-option')]
        .filter((element) => element.textContent.trim() === optionText).at(-1);
    await click(option);
}

const clickOk = () => click(document.querySelector('.ant-modal-footer .ant-btn-primary'));
const clickCancel = () => click(document.querySelector('.ant-modal-footer .ant-btn-default'));

describe('DcaPlanFormModal', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    const render = async (props = {}) => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <DcaPlanFormModal open editing={null} onOk={vi.fn()} onCancel={vi.fn()} confirmLoading={false} {...props}/>,
        ));
        await act(async () => flush());
    };

    it('新建时预填默认值，并按固定金额提交空的可选参考字段', async () => {
        const onOk = vi.fn();
        await render({onOk});

        expect(document.querySelector('.ant-modal-title').textContent).toBe('新建定投计划');
        expect(document.querySelector('#amount').value).toBe('1000.00');
        expect(formItemByLabel('频率').textContent).toContain('周定投');
        expect(formItemByLabel('定投日').textContent).toContain('周一');
        expect(formItemByLabel('定投日(每月几号,1-28)')).toBeUndefined();
        expect(formItemByLabel('参考指数代码')).toBeUndefined();

        await clickOk();

        expect(onOk).toHaveBeenCalledWith(expect.objectContaining({
            enabled: true,
            amount: 1000,
            frequency: 'WEEKLY',
            dayOfWeek: 1,
            amountStrategy: 'FIXED',
            referenceIndexCode: null,
            movingAverageDays: null,
        }));
    });

    it('选择均线策略后展开参考指数与均线周期，并原样提交', async () => {
        const onOk = vi.fn();
        await render({onOk, benchmarkIndexCode: '000905.SH'});

        expect(document.querySelector('.ant-segmented-item-disabled')).toBeNull();

        await selectSegmented('均线');
        expect(formItemByLabel('参考指数代码')).toBeDefined();
        expect(formItemByLabel('均线周期').textContent).toContain('250 日均线');

        await setInputValue(document.querySelector('#referenceIndexCode'), '000300.SH');
        await clickOk();

        expect(onOk).toHaveBeenCalledWith(expect.objectContaining({
            amountStrategy: 'MOVING_AVERAGE',
            referenceIndexCode: '000300.SH',
            movingAverageDays: 250,
        }));
    });

    it('未提供基准指数时低估策略不可选，编辑态预填金额并支持取消', async () => {
        const onOk = vi.fn();
        const onCancel = vi.fn();
        await render({
            onOk,
            onCancel,
            editing: {id: 7, amount: 500, frequency: 'DAILY', amountStrategy: 'FIXED'},
        });

        expect(document.querySelector('.ant-modal-title').textContent).toBe('编辑定投计划');
        expect(document.querySelector('#amount').value).toBe('500.00');
        expect(formItemByLabel('定投日')).toBeUndefined();
        const disabled = [...document.querySelectorAll('.ant-segmented-item')]
            .filter((element) => element.className.includes('ant-segmented-item-disabled'));
        expect(disabled.map((element) => element.textContent.trim())).toEqual(['低估']);

        await clickCancel();
        expect(onCancel).toHaveBeenCalled();
        expect(onOk).not.toHaveBeenCalled();
    });

    it('切换到月定投时展开每月几号并提交所选日期', async () => {
        const onOk = vi.fn();
        await render({onOk});

        await selectOption('频率', '月定投');
        const dayOfMonth = formItemByLabel('定投日(每月几号,1-28)');
        expect(dayOfMonth).toBeDefined();
        expect(formItemByLabel('定投日')).toBeUndefined();

        await setInputValue(dayOfMonth.querySelector('input'), '15');
        await clickOk();

        expect(onOk).toHaveBeenCalledWith(expect.objectContaining({
            frequency: 'MONTHLY',
            dayOfMonth: 15,
        }));
    });
});