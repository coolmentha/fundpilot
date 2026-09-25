import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

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
window.getComputedStyle = () => ({width: '0px', getPropertyValue: () => ''});
Element.prototype.scrollIntoView = vi.fn();

const {default: AlertRuleFormModal} = await import('./AlertRuleFormModal.jsx');

const FUNDS = [{portfolioFundId: 41, fundName: '招商中证白酒', fundCode: '161725'}];

// 指标元数据的形状与 GET /api/alert-rules/indicators 一致。
const INDICATORS = [
    {
        code: 'DAILY_CHANGE', label: '当日涨跌幅', description: '最新净值相对上一交易日的涨跌幅',
        source: 'FUND_FACT', minimum: -1, maximum: 1, parameters: [],
        relations: [
            {relation: 'ABOVE', label: '高于', thresholded: true, defaultThreshold: 0.05},
            {relation: 'BELOW', label: '低于', thresholded: true, defaultThreshold: -0.05},
        ],
    },
    {
        code: 'PRICE_VS_MA', label: '净值与均线偏离率', description: '累计净值相对近 N 个交易日均线的偏离率',
        source: 'MARKET_DATA', minimum: -1, maximum: 5,
        parameters: [{name: 'window', label: '均线窗口（交易日）', defaultValue: 250, minimum: 5, maximum: 250}],
        relations: [
            {relation: 'ABOVE', label: '高于', thresholded: true, defaultThreshold: 0},
            {relation: 'BELOW', label: '低于', thresholded: true, defaultThreshold: 0},
            {relation: 'CROSS_BELOW', label: '下穿均线', thresholded: true, defaultThreshold: 0},
        ],
    },
    {
        code: 'HOLDING_RETURN', label: '持仓收益率', description: '当前持仓的浮动收益率',
        source: 'FUND_FACT', minimum: -1, maximum: 5, parameters: [],
        relations: [
            {relation: 'ABOVE', label: '高于', thresholded: true, defaultThreshold: 0.15},
            {relation: 'INCREASING', label: '较前值放大', thresholded: false, defaultThreshold: null},
        ],
    },
    {
        code: 'WEEKLY_MACD_HISTOGRAM', label: '周线 MACD 柱高', description: '周线 MACD 柱：正为红柱、负为绿柱',
        source: 'MARKET_DATA', minimum: -100, maximum: 100,
        parameters: [
            {name: 'fast', label: '快线周期（周）', defaultValue: 12, minimum: 2, maximum: 60},
            {name: 'slow', label: '慢线周期（周）', defaultValue: 26, minimum: 3, maximum: 120},
            {name: 'signal', label: '信号线周期（周）', defaultValue: 9, minimum: 2, maximum: 60},
        ],
        relations: [
            {relation: 'ABOVE', label: '红柱', thresholded: true, defaultThreshold: 0},
            {relation: 'BELOW', label: '绿柱', thresholded: true, defaultThreshold: 0},
            {relation: 'INCREASING', label: '柱较上周放大', thresholded: false, defaultThreshold: null},
            {relation: 'DECREASING', label: '柱较上周缩小', thresholded: false, defaultThreshold: null},
        ],
    },
];

const PREVIEW = {
    tradingDate: '2026-09-25T00:00:00Z',
    hit: true,
    funds: [{
        portfolioFundId: 41, fundCode: '161725', fundName: '招商中证白酒', hit: true,
        conditions: [{text: '当日涨跌幅 高于 0.05', currentValue: 0.0732, satisfied: true}],
    }],
};

const click = async (element) => {
    await act(async () => {
        element.click();
        await Promise.resolve();
    });
};

const openSelect = async (element) => {
    await act(async () => {
        element.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
        await Promise.resolve();
    });
};

const setNumber = async (input, value) => {
    await act(async () => {
        const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
        setter.call(input, String(value));
        input.dispatchEvent(new Event('input', {bubbles: true}));
        input.dispatchEvent(new Event('change', {bubbles: true}));
        await Promise.resolve();
    });
};

/** 表单校验是异步的，错误提示要等下一次事件循环才渲染。 */
const flush = async () => {
    await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 0));
    });
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
            <App>
                <AlertRuleFormModal open editing={null} funds={FUNDS} indicators={INDICATORS}
                                    confirmLoading={false} onPreview={vi.fn().mockResolvedValue(PREVIEW)}
                                    onCancel={vi.fn()} {...props}/>
            </App>,
        ));
    };

    const clickButton = async (label) => {
        const button = [...document.querySelectorAll('.ant-modal button')]
            .find((item) => item.textContent.replace(/\s/g, '') === label);
        await click(button);
    };

    const selectOption = async (selectIndex, optionLabel) => {
        const select = document.querySelectorAll('.ant-modal .ant-select')[selectIndex];
        await openSelect(select);
        const option = [...document.querySelectorAll('.ant-select-item-option')]
            .find((item) => item.textContent.trim() === optionLabel);
        await click(option);
    };

    const numberInputs = () => [...document.querySelectorAll('.ant-modal .ant-input-number-input')];

    // 规则种类单选位于向导第一步，按取值点击对应单选框。
    const selectKind = async (value) => {
        await click(document.querySelector(`.ant-modal input[type="radio"][value="${value}"]`));
    };

    it('条件行按指标提供关系与参数，并把阈值原样提交为小数', async () => {
        const onOk = vi.fn();
        await render({onOk});

        await clickButton('下一步');
        await clickButton('添加条件');

        expect(document.body.textContent).toContain('最新净值相对上一交易日的涨跌幅');
        expect(document.body.textContent).toContain('全部满足才会提醒');

        await clickButton('下一步');
        await clickButton('创建');

        expect(onOk).toHaveBeenCalledWith({
            scope: 'GLOBAL',
            portfolioFundId: null,
            kind: 'CONDITION',
            match: 'ALL',
            conditions: [{indicator: 'DAILY_CHANGE', relation: 'ABOVE', params: {}, value: 0.05}],
            enabled: true,
        });
        // 条件提醒带条件、不带止盈参数。
        expect(onOk.mock.calls[0][0]).not.toHaveProperty('takeProfit');
    });

    it('切换指标后按新指标的默认参数与默认阈值重建该条条件', async () => {
        const onOk = vi.fn();
        await render({onOk});

        await clickButton('下一步');
        await clickButton('添加条件');
        await selectOption(0, '净值与均线偏离率');

        expect(document.body.textContent).toContain('均线窗口（交易日）（5–250）');
        expect(Number(numberInputs()[0].value)).toBe(250);
        expect(Number(numberInputs()[1].value)).toBe(0);

        await selectOption(1, '下穿均线');
        await setNumber(numberInputs()[1], -0.02);
        await clickButton('下一步');
        await clickButton('创建');

        expect(onOk).toHaveBeenCalledWith({
            scope: 'GLOBAL',
            portfolioFundId: null,
            kind: 'CONDITION',
            match: 'ALL',
            conditions: [{indicator: 'PRICE_VS_MA', relation: 'CROSS_BELOW', params: {window: 250}, value: -0.02}],
            enabled: true,
        });
    });

    it('不比较阈值的关系不渲染阈值输入框', async () => {
        await render({onOk: vi.fn()});

        await clickButton('下一步');
        await clickButton('添加条件');
        await selectOption(0, '持仓收益率');
        await selectOption(1, '较前值放大');

        expect(numberInputs()).toHaveLength(0);
        expect(document.body.textContent).not.toContain('阈值为小数');
    });

    it('比较阈值但指标未给默认值时必须由用户填写', async () => {
        await render({
            onOk: vi.fn(),
            indicators: [{
                ...INDICATORS[2],
                relations: [{relation: 'ABOVE', label: '高于', thresholded: true, defaultThreshold: null}],
            }],
        });

        await clickButton('下一步');
        await clickButton('添加条件');
        await clickButton('下一步');
        await flush();

        expect(document.body.textContent).toContain('请填写阈值');
        expect([...document.querySelectorAll('.ant-modal button')]
            .some((button) => button.textContent.replace(/\s/g, '') === '创建')).toBe(false);
    });

    it('套用逻辑破坏止损模板后按元数据填入三条条件', async () => {
        const onOk = vi.fn();
        await render({onOk});

        await selectKind('LOGIC_BROKEN');
        await clickButton('下一步');
        await clickButton('套用逻辑破坏止损模板');

        // 指标与关系文案来自指标元数据，前端不硬编码第二份枚举。
        expect(document.body.textContent).toContain('净值与均线偏离率');
        expect(document.body.textContent).toContain('周线 MACD 柱高');
        expect(document.body.textContent).toContain('绿柱');
        expect(document.body.textContent).toContain('柱较上周缩小');
        // 非主动型基金的隐含条件在这里提示但不渲染成条件行。
        expect(document.body.textContent).toContain('放量下跌');

        await clickButton('下一步');
        await clickButton('创建');

        const payload = onOk.mock.calls[0][0];
        expect(payload).toEqual({
            scope: 'GLOBAL',
            portfolioFundId: null,
            kind: 'LOGIC_BROKEN',
            match: 'ALL',
            conditions: [
                {indicator: 'PRICE_VS_MA', relation: 'BELOW', params: {window: 250}, value: 0},
                {indicator: 'WEEKLY_MACD_HISTOGRAM', relation: 'BELOW', params: {fast: 12, slow: 26, signal: 9}, value: 0},
                {indicator: 'WEEKLY_MACD_HISTOGRAM', relation: 'DECREASING', params: {fast: 12, slow: 26, signal: 9}, value: null},
            ],
            enabled: true,
        });
        // 逻辑破坏止损带条件、不带止盈参数。
        expect(payload).not.toHaveProperty('takeProfit');
    });

    it('切换回撤止盈并套用行业指数预设后填入六个参数', async () => {
        const onOk = vi.fn();
        await render({onOk});

        await selectKind('TRAILING_STOP');
        await clickButton('下一步');

        expect(document.body.textContent).toContain('配置止盈参数');
        expect(document.body.textContent).toContain('行业指数 20%/8%');

        await clickButton('套用「行业指数20%/8%」');

        // 六参数按 activation/pullback/harvest/minimumHolding/maxSingleSell/cooldownDays 顺序，比例用小数。
        expect(numberInputs().map((input) => Number(input.value))).toEqual([0.2, 0.08, 0.5, 0.4, 0.2, 10]);

        await clickButton('下一步');
        await clickButton('创建');

        const payload = onOk.mock.calls[0][0];
        expect(payload).toEqual({
            scope: 'GLOBAL',
            portfolioFundId: null,
            kind: 'TRAILING_STOP',
            match: 'ALL',
            takeProfit: {activation: 0.2, pullback: 0.08, harvest: 0.5, minimumHolding: 0.4, maxSingleSell: 0.2, cooldownDays: 10},
            enabled: true,
        });
        // 回撤止盈只带止盈参数、不带条件。
        expect(payload).not.toHaveProperty('conditions');
    });

    it('编辑已有规则时回显条件并保留启用状态', async () => {
        const onOk = vi.fn();
        const editing = {
            id: 3,
            scope: 'FUND',
            portfolioFundId: 41,
            conditions: [{indicator: 'DAILY_CHANGE', relation: 'BELOW', params: {}, value: -0.05}],
            enabled: false,
        };
        await render({editing, onOk});

        expect(document.querySelector('.ant-modal input[type="radio"][value="FUND"]').checked).toBe(true);
        await clickButton('下一步');

        expect(Number(numberInputs()[0].value)).toBe(-0.05);

        await clickButton('下一步');
        await clickButton('保存');

        expect(onOk).toHaveBeenCalledWith({
            scope: 'FUND',
            portfolioFundId: 41,
            kind: 'CONDITION',
            match: 'ALL',
            conditions: [{indicator: 'DAILY_CHANGE', relation: 'BELOW', params: {}, value: -0.05}],
            enabled: false,
        });
    });

    it('预览步骤用最近交易日试算并展示逐条条件的现值', async () => {
        const onPreview = vi.fn().mockResolvedValue(PREVIEW);
        await render({onOk: vi.fn(), onPreview});

        await clickButton('下一步');
        await clickButton('添加条件');
        await clickButton('下一步');
        await act(async () => {
            await Promise.resolve();
        });

        expect(onPreview).toHaveBeenCalledWith({
            scope: 'GLOBAL',
            portfolioFundId: null,
            kind: 'CONDITION',
            match: 'ALL',
            conditions: [{indicator: 'DAILY_CHANGE', relation: 'ABOVE', params: {}, value: 0.05}],
            enabled: true,
        });
        expect(document.body.textContent).toContain('当前满足条件');
        expect(document.body.textContent).toContain('当日涨跌幅 高于 0.05');
        expect(document.body.textContent).toContain('现值 0.0732');

        await clickButton('重新试算');
        expect(onPreview).toHaveBeenCalledTimes(2);
    });
});