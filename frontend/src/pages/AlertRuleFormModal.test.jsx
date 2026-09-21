import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

vi.mock('../api/hooks.js', () => ({}));

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

const {default: AlertRuleFormModal} = await import('./AlertRuleFormModal.jsx');

const FUNDS = [{portfolioFundId: 41, fundName: '招商中证白酒', fundCode: '161725'}];

const setValue = (input, value) => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', {bubbles: true}));
};

describe('AlertRuleFormModal', () => {
    let container;
    let root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    const render = async (props) => {
        await act(async () => root.render(
            <App><AlertRuleFormModal open editing={null} funds={FUNDS} confirmLoading={false} {...props}/></App>,
        ));
    };

    const clickButton = async (label) => {
        const button = [...document.querySelectorAll('.ant-modal button')]
            .find((item) => item.textContent.replace(/\s/g, '') === label);
        await act(async () => button.click());
    };

    const clickRadio = async (value) => {
        const radio = document.querySelector(`.ant-modal input[type="radio"][value="${value}"]`);
        await act(async () => radio.click());
    };

    it('三步向导按顺序收集范围、类型、阈值并把百分比换算成小数', async () => {
        const onOk = vi.fn();
        await render({onOk, onCancel: vi.fn()});

        expect(document.body.textContent).toContain('选择范围');
        await clickButton('下一步');

        await clickRadio('RISE');
        await clickButton('下一步');

        const thresholdInput = document.querySelector('.ant-modal input[role="spinbutton"]');
        await act(async () => setValue(thresholdInput, '5'));
        await clickButton('创建');

        expect(onOk).toHaveBeenCalledWith({
            scope: 'GLOBAL',
            portfolioFundId: null,
            ruleType: 'RISE',
            threshold: 0.05,
            enabled: true,
        });
    });

    it('选择指定基金后才显示基金下拉', async () => {
        await render({onOk: vi.fn(), onCancel: vi.fn()});

        expect(document.querySelector('.ant-modal .ant-select')).toBeNull();
        await clickRadio('FUND');

        expect(document.querySelector('.ant-modal .ant-select')).not.toBeNull();
    });

    it('编辑已有规则时预填阈值并保留启用状态', async () => {
        const onOk = vi.fn();
        const editing = {
            id: 3,
            scope: 'FUND',
            portfolioFundId: 41,
            ruleType: 'PROFIT',
            threshold: 0.03,
            enabled: false,
        };
        await render({editing, onOk, onCancel: vi.fn()});

        await clickButton('下一步');
        expect(document.body.textContent).toContain('盈利提醒仅对已持仓基金生效');
        await clickButton('下一步');

        const thresholdInput = document.querySelector('.ant-modal input[role="spinbutton"]');
        expect(Number(thresholdInput.value)).toBe(3);

        await clickButton('保存');
        expect(onOk).toHaveBeenCalledWith({
            scope: 'FUND',
            portfolioFundId: 41,
            ruleType: 'PROFIT',
            threshold: 0.03,
            enabled: false,
        });
    });
});
