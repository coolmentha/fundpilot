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
        takeProfit: null, grid: null,
        enabled: true, todaySent: false, lastTriggeredAt: null,
    },
    {
        // 网格的覆盖口径用两个比例：单基金网格规则应与同比例的全局网格规则同口径。
        id: 5, scope: 'FUND', portfolioFundId: 43, fundCode: '110022', fundName: '易方达消费行业',
        kind: 'GRID', kindLabel: '网格交易提醒', match: 'ALL',
        conditionSummary: '网格交易：以最近一笔成交净值为基准，上浮 5% 提醒卖出，下降 3% 提醒买入',
        conditions: [], takeProfit: null, grid: {upRatio: 0.05, downRatio: 0.03},
        enabled: true, todaySent: false, lastTriggeredAt: null,
    },
    {
        // 与 id=5 两个比例都相同，应被标记为已覆盖。
        id: 6, scope: 'GLOBAL', portfolioFundId: null, fundCode: null, fundName: null,
        kind: 'GRID', kindLabel: '网格交易提醒', match: 'ALL',
        conditionSummary: '网格交易：以最近一笔成交净值为基准，上浮 5% 提醒卖出，下降 3% 提醒买入',
        conditions: [], takeProfit: null, grid: {upRatio: 0.05, downRatio: 0.03},
        enabled: true, todaySent: false, lastTriggeredAt: null,
    },
    {
        // 上浮比例与 id=5 不同，口径不同，不应被标记为已覆盖。
        id: 7, scope: 'GLOBAL', portfolioFundId: null, fundCode: null, fundName: null,
        kind: 'GRID', kindLabel: '网格交易提醒', match: 'ALL',
        conditionSummary: '网格交易：以最近一笔成交净值为基准，上浮 8% 提醒卖出，下降 3% 提醒买入',
        conditions: [], takeProfit: null, grid: {upRatio: 0.08, downRatio: 0.03},
        enabled: true, todaySent: false, lastTriggeredAt: null,
    },
    {
        // 已停用 + 今日已提醒：用于验证启用状态与今日提醒状态两个维度。
        id: 8, scope: 'FUND', portfolioFundId: 45, fundCode: '888888', fundName: '已停用的基金',
        kind: 'CONDITION', kindLabel: '条件提醒', match: 'ALL', conditionSummary: '当日涨跌幅 低于 -0.03',
        conditions: [{indicator: 'DAILY_CHANGE', params: {}, relation: 'BELOW', value: -0.03}],
        takeProfit: null, grid: null,
        enabled: false, todaySent: true, lastTriggeredAt: '2026-09-18T06:30:00Z',
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

const funds = [
    {portfolioFundId: 41, fundCode: '161725', fundName: '招商中证白酒'},
    {portfolioFundId: 42, fundCode: '000300', fundName: '沪深300指数'},
    {portfolioFundId: 43, fundCode: '110022', fundName: '易方达消费行业'},
    {portfolioFundId: 45, fundCode: '888888', fundName: '已停用的基金'},
    // 没有任何规则关联的基金：用于验证筛选无命中时的空状态。
    {portfolioFundId: 44, fundCode: '999999', fundName: '未配置规则的基金'},
];

// 可变数据源：个别用例需要换成「只有单基金规则」的集合来触发筛选空状态。
let currentRules = rules;

vi.mock('../api/hooks.js', () => ({
    useAlertRules: () => ({data: currentRules, isLoading: false, isError: false, refetch: vi.fn()}),
    useAlertNotifications: () => ({data: notifications, isLoading: false, isError: false, refetch: vi.fn()}),
    useAlertIndicatorMetadata: () => ({data: indicators, isLoading: false, isError: false, refetch: vi.fn()}),
    useFunds: () => ({data: funds, isLoading: false, isError: false, refetch: vi.fn()}),
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
        currentRules = rules;
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
        // 命中的是 id=1（条件口径）与 id=6（网格口径，与单基金网格规则两个比例都相同）。
        expect(container.textContent.split('已被单基金规则覆盖')).toHaveLength(3);
    });

    it('网格规则按两个比例的覆盖口径分别判定，比例不同不算同口径', async () => {
        await render();

        expect(container.textContent).toContain('网格交易提醒');
        expect(container.textContent).toContain('上浮 5% 提醒卖出');

        // 只看全局规则行（含「所有关注基金」），单基金规则行不参与覆盖判定。
        const globalRows = [...container.querySelectorAll('tbody tr')]
            .filter((row) => row.textContent.includes('所有关注基金'));
        const covered = (text) => globalRows
            .filter((row) => row.textContent.includes(text))
            .map((row) => row.textContent.includes('已被单基金规则覆盖'));

        // id=6 上浮 5%/下降 3% 与 id=5 完全同口径 → 已覆盖
        expect(covered('上浮 5% 提醒卖出')).toEqual([true]);
        // id=7 上浮 8% → 口径不同，不被覆盖
        expect(covered('上浮 8% 提醒卖出')).toEqual([false]);
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

    // 规则行文本（去掉空白）。排除空状态占位行，否则它会混进行数断言。
    const ruleRowLabels = () => [...container.querySelectorAll('.alert-rules-card tbody tr')]
        .filter((row) => !row.classList.contains('ant-table-placeholder'))
        .map((row) => row.textContent.replace(/\s/g, ''));

    // 规则表格内的文本。整个容器的 textContent 会带上「提醒记录」与下拉选项，断言必须限定范围。
    const rulesTableText = () => [...container.querySelectorAll('.alert-rules-card tbody tr')]
        .filter((row) => !row.classList.contains('ant-table-placeholder'))
        .map((row) => row.textContent).join(' ');

    /**
     * 打开某个多选筛选下拉并勾选选项（antd Select 在根节点用 mousedown 展开）。
     * index 对应筛选栏中 Select 的顺序：0=基金，1=提醒类型。
     */
    const selectFilterOptions = async (index, ...labels) => {
        const select = container.querySelectorAll('.alert-rule-filter')[index];
        await act(async () => {
            select.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
            await Promise.resolve();
        });
        for (const label of labels) {
            const option = [...document.querySelectorAll('.ant-select-item-option')]
                .find((item) => item.textContent.replace(/\s/g, '') === label.replace(/\s/g, ''));
            await act(async () => {
                option.dispatchEvent(new MouseEvent('click', {bubbles: true}));
                await Promise.resolve();
            });
        }
    };

    const selectFunds = (...labels) => selectFilterOptions(0, ...labels);

    /** 点击 Segmented 三态筛选（按 aria-label 定位，再按文案点选）。 */
    const selectSegmented = async (ariaLabel, label) => {
        const group = container.querySelector(`[aria-label="${ariaLabel}"]`);
        const item = [...group.querySelectorAll('label, .ant-segmented-item')]
            .find((node) => node.textContent.replace(/\s/g, '') === label);
        await act(async () => {
            item.dispatchEvent(new MouseEvent('click', {bubbles: true}));
            await Promise.resolve();
        });
    };

    it('未选择基金时展示全部规则', async () => {
        await render();

        expect(ruleRowLabels()).toHaveLength(rules.length);
    });

    it('按基金筛选时保留全局规则并只显示所选基金的单基金规则', async () => {
        await render();
        const globalRows = () => ruleRowLabels().filter((text) => text.includes('所有关注基金')).length;

        await selectFunds('招商中证白酒161725');

        // 只剩 id=2（招商中证白酒）与 4 条全局规则。
        expect(ruleRowLabels()).toHaveLength(1 + globalRows());
        expect(container.textContent).toContain('招商中证白酒');
        expect(container.textContent).not.toContain('沪深300指数');
        expect(container.textContent).not.toContain('易方达消费行业');
    });

    it('多选基金时按并集显示各基金的单基金规则', async () => {
        await render();

        await selectFunds('招商中证白酒161725', '沪深300指数000300');

        expect(container.textContent).toContain('招商中证白酒');
        expect(container.textContent).toContain('沪深300指数');
        // 未选中的基金规则仍被过滤掉。
        expect(container.textContent).not.toContain('易方达消费行业');
    });

    it('筛选到没有关联规则的基金时给出筛选专属空状态', async () => {
        // 全局规则会被一直保留，因此只有「没有全局规则 + 该基金没有单基金规则」才会空。
        currentRules = rules.filter((rule) => rule.scope === 'FUND');
        await render();

        await selectFunds('未配置规则的基金999999');

        expect(container.textContent).toContain('没有符合筛选条件的提醒规则');
    });

    it('没有单基金规则时选中基金不会误报空状态，全局规则仍显示', async () => {
        currentRules = rules.filter((rule) => rule.scope === 'GLOBAL');
        await render();

        await selectFunds('招商中证白酒161725');

        expect(container.textContent).not.toContain('没有符合筛选条件的提醒规则');
        expect(ruleRowLabels().every((text) => text.includes('所有关注基金'))).toBe(true);
    });

    it('按提醒类型筛选，类型选项由后端 kindLabel 派生', async () => {
        await render();

        await selectFilterOptions(1, '网格交易提醒');

        // 只剩 3 条网格规则（id=5/6/7），条件提醒全部被筛掉。
        expect(ruleRowLabels()).toHaveLength(3);
        expect(ruleRowLabels().every((text) => text.includes('网格交易提醒'))).toBe(true);
        expect(rulesTableText()).toContain('上浮 5% 提醒卖出');
        expect(rulesTableText()).not.toContain('当日涨跌幅 高于 0.05');
    });

    it('提醒类型可多选，按并集显示', async () => {
        await render();

        await selectFilterOptions(1, '网格交易提醒', '回撤止盈');

        expect(ruleRowLabels()).toHaveLength(4); // 3 条网格 + 1 条回撤止盈
        expect(rulesTableText()).toContain('回撤止盈');
        expect(rulesTableText()).not.toContain('逻辑破坏止损');
    });

    it('按启用状态筛选', async () => {
        await render();

        await selectSegmented('按启用状态筛选', '已停用');

        expect(ruleRowLabels()).toHaveLength(1);
        expect(rulesTableText()).toContain('已停用的基金');
    });

    it('按今日提醒状态筛选', async () => {
        await render();

        await selectSegmented('按今日提醒状态筛选', '已提醒');

        // id=1（全局条件提醒）与 id=8（已停用基金）今日均已提醒。
        expect(ruleRowLabels()).toHaveLength(2);
        expect(rulesTableText()).toContain('已停用的基金');
        expect(rulesTableText()).toContain('所有关注基金');
        expect(rulesTableText()).not.toContain('沪深300指数');
    });

    it('多个维度之间是「且」的关系', async () => {
        await render();

        // 网格 + 已停用：没有任何网格规则被停用，结果为空。
        await selectFilterOptions(1, '网格交易提醒');
        await selectSegmented('按启用状态筛选', '已停用');

        expect(ruleRowLabels()).toHaveLength(0);
        expect(container.textContent).toContain('没有符合筛选条件的提醒规则');
    });

    it('重置筛选清空所有维度', async () => {
        await render();

        await selectFilterOptions(1, '网格交易提醒');
        expect(ruleRowLabels()).toHaveLength(3);

        await act(async () => [...container.querySelectorAll('button')]
            .find((button) => button.textContent === '重置筛选').click());

        expect(ruleRowLabels()).toHaveLength(rules.length);
    });

    it('没有筛选条件时重置按钮不可用', async () => {
        await render();

        const reset = [...container.querySelectorAll('button')]
            .find((button) => button.textContent === '重置筛选');
        expect(reset.disabled).toBe(true);
    });
});
