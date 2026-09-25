import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const {setEnabled, deleteRule, createRule} = vi.hoisted(() => ({
    setEnabled: vi.fn(),
    deleteRule: vi.fn(),
    createRule: vi.fn(),
}));

const rules = [
    {
        id: 1, scope: 'GLOBAL', portfolioFundId: null, fundCode: null, fundName: null,
        kind: 'CONDITION', kindLabel: '条件提醒', match: 'ALL', conditionSummary: '当日涨跌幅 高于 0.05',
        conditions: [{indicator: 'DAILY_CHANGE', params: {}, relation: 'ABOVE', value: 0.05}],
        takeProfit: null,
        enabled: true, todaySent: true, lastTriggeredAt: '2026-09-18T06:30:00Z',
    },
    {
        id: 2, scope: 'FUND', portfolioFundId: 41, fundCode: '161725', fundName: '招商中证白酒',
        kind: 'CONDITION', kindLabel: '条件提醒', match: 'ALL', conditionSummary: '当日涨跌幅 高于 0.05',
        conditions: [{indicator: 'DAILY_CHANGE', params: {}, relation: 'ABOVE', value: 0.05}],
        takeProfit: null,
        enabled: true, todaySent: false, lastTriggeredAt: null,
    },
    {
        id: 3, scope: 'FUND', portfolioFundId: 42, fundCode: '000300', fundName: '沪深300指数',
        kind: 'TRAILING_STOP', kindLabel: '回撤止盈', match: 'ALL',
        conditionSummary: '回撤止盈：启动 20%、回撤 8%、收割 50%、最低保留 40%、单次卖出上限 20%、冷静期 10 个交易日',
        conditions: [],
        takeProfit: {activation: 0.2, pullback: 0.08, harvest: 0.5, minimumHolding: 0.4, maxSingleSell: 0.2, cooldownDays: 10},
        enabled: true, todaySent: false, lastTriggeredAt: null,
    },
    {
        // 与 id=1 条件相同但种类不同：口径签名含种类，不应被误判为已覆盖。
        id: 4, scope: 'GLOBAL', portfolioFundId: null, fundCode: null, fundName: null,
        kind: 'LOGIC_BROKEN', kindLabel: '逻辑破坏止损', match: 'ALL',
        conditionSummary: '逻辑破坏止损：当日涨跌幅 高于 0.05',
        conditions: [{indicator: 'DAILY_CHANGE', params: {}, relation: 'ABOVE', value: 0.05}],
        takeProfit: null,
        enabled: true, todaySent: false, lastTriggeredAt: null,
    },
];

const notifications = [
    {
        id: 9, alertRuleId: 2, ruleType: null, threshold: null,
        triggerSummary: '招商中证白酒(161725) 命中：当日涨跌幅 高于 0.05，现值 0.0732',
        fundCount: 1, status: 'SENT',
        failureReason: null, tradingDate: '2026-09-18T00:00:00Z', sentAt: '2026-09-18T06:30:00Z',
        recipientEmail: 'a@b.com',
    },
    {
        id: 8, alertRuleId: 1, ruleType: null, threshold: null,
        triggerSummary: null, fundCount: 0, status: 'FAILED',
        failureReason: 'SMTP 连接失败', tradingDate: '2026-09-17T00:00:00Z', sentAt: null,
        recipientEmail: null,
    },
];

const indicators = [
    {
        code: 'DAILY_CHANGE', label: '当日涨跌幅', description: '最新净值相对上一交易日的涨跌幅',
        source: 'FUND_FACT', minimum: -1, maximum: 1, parameters: [],
        relations: [{relation: 'ABOVE', label: '高于', thresholded: true, defaultThreshold: 0.05}],
    },
];

vi.mock('../api/hooks.js', () => ({
    useAlertRules: () => ({data: rules, isLoading: false, isError: false, refetch: vi.fn()}),
    useAlertNotifications: () => ({data: notifications, isLoading: false, isError: false, refetch: vi.fn()}),
    useAlertIndicatorMetadata: () => ({data: indicators, isLoading: false, isError: false, refetch: vi.fn()}),
    useFunds: () => ({data: [], isLoading: false, isError: false, refetch: vi.fn()}),
    useCreateAlertRule: () => ({mutateAsync: createRule, isPending: false}),
    useUpdateAlertRule: () => ({mutateAsync: vi.fn(), isPending: false}),
    useSetAlertRuleEnabled: () => ({mutateAsync: setEnabled, isPending: false}),
    useDeleteAlertRule: () => ({mutateAsync: deleteRule, isPending: false}),
    usePreviewAlertRule: () => ({mutateAsync: vi.fn(), isPending: false}),
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

const {default: AlertRulesPage} = await import('./AlertRulesPage.jsx');

describe('AlertRulesPage', () => {
    let container;
    let root;

    beforeEach(() => {
        setEnabled.mockReset().mockResolvedValue(undefined);
        deleteRule.mockReset().mockResolvedValue(undefined);
        createRule.mockReset().mockResolvedValue(undefined);
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
            <MemoryRouter><App><AlertRulesPage/></App></MemoryRouter>,
        ));
    };

    it('展示规则与提醒记录，并标注被单基金规则覆盖的全局规则', async () => {
        await render();

        expect(container.textContent).toContain('已被单基金规则覆盖');
        expect(container.textContent).toContain('招商中证白酒');
        expect(container.textContent).toContain('当日涨跌幅 高于 0.05');
        expect(container.textContent).toContain('已提醒');
        expect(container.textContent).toContain('未提醒');
        expect(container.textContent).toContain('已发送');
        expect(container.textContent).toContain('发送失败');
        expect(container.textContent).toContain('SMTP 连接失败');
        expect(container.textContent).toContain('共 1 只基金');

        const fundLink = [...container.querySelectorAll('a')]
            .find((link) => link.textContent === '招商中证白酒');
        expect(fundLink.getAttribute('href')).toBe('/funds/41');

        // 覆盖口径签名含规则种类：同为全局规则、条件相同但种类不同不会被误标为已覆盖。
        expect(container.textContent.split('已被单基金规则覆盖')).toHaveLength(2);
    });

    it('列表展示规则种类与回撤止盈规则的参数摘要', async () => {
        await render();

        // 种类中文名直接用后端 kindLabel，回撤止盈的摘要也复用后端 conditionSummary。
        expect(container.textContent).toContain('条件提醒');
        expect(container.textContent).toContain('回撤止盈');
        expect(container.textContent).toContain('启动 20%');
        expect(container.textContent).toContain('冷静期 10 个交易日');
    });

    it('切换开关按启用状态调用启用或停用接口', async () => {
        await render();

        await act(async () => container.querySelector('.ant-switch').click());

        expect(setEnabled).toHaveBeenCalledWith({id: 1, action: 'disable'});
    });

    it('删除规则需要二次确认', async () => {
        await render();

        await act(async () => container.querySelector('button[aria-label="删除规则"]').click());
        const confirmButton = [...document.querySelectorAll('.ant-modal-confirm-btns button')]
            .find((button) => button.textContent.replace(/\s/g, '') === '删除');
        await act(async () => confirmButton.click());

        expect(deleteRule).toHaveBeenCalledWith(1);
    });

    it('新建规则从向导第一步开始', async () => {
        await render();

        await act(async () => [...container.querySelectorAll('button')]
            .find((button) => button.textContent === '新建提醒规则').click());

        expect(document.querySelector('.ant-modal input[type="radio"][value="GLOBAL"]').checked).toBe(true);
        expect(document.querySelector('.ant-modal-footer')).not.toBeNull();
    });
});
