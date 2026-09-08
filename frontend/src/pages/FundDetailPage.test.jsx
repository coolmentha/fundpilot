import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {afterEach, describe, expect, it, vi} from 'vitest';

const optional = vi.hoisted(() => ({failed: false}));
vi.mock('../api/hooks.js', () => ({
    useFund: () => ({
        data: {
            id: 1, portfolioFundId: 11, fundCode: '000001', fundName: '测试基金', fundCategory: 'INDEX', fundSubType: 'INDEX', status: 'HOLDING',
            holdingShares: 100, holdingAmount: 80, totalPnl: -20, dailyPnl: 1.5, dailyChangePct: 0.02,
            valuationSource: 'CONFIRMED_NAV',
            valuationDate: '2026-07-24T00:00:00Z', valuationNav: 1, positionWarningEnabled: true,
        },
        isLoading: false, isError: false, refetch: vi.fn(),
    }),
    useFundFeeRates: () => optional.failed ? ({isError: true, refetch: vi.fn()}) : ({
        isError: false,
        data: {
            purchaseRate: 0.015, discountRate: 0.0015, salesServiceFee: 0.003,
            redemptionLadder: [{maxDays: 7, rate: 0.015}, {maxDays: null, rate: 0}],
            managementFee: 0, custodyFee: null, purchaseStatus: 'LIMITED', purchaseLimit: 1000,
            minimumPurchaseAmount: 10, status: 'SUCCESS', stale: true,
            channelReference: {label: '天天基金渠道参考', sourceName: '天天基金'},
        },
    }),
    useFundResearch: () => optional.failed ? ({isError: true, refetch: vi.fn()}) : ({
        isLoading: false, isError: false, refetch: vi.fn(),
        data: {
            profile: {status: 'SUCCESS', stale: false, reportDate: '2026-08-31T00:00:00Z', source: {name: '天天基金'}, data: {
                fundCategory: 'INDEX', shareClass: 'C', trackingIndex: {code: '000300', name: '沪深300'},
                targetEtf: {code: '510300', name: '沪深300ETF'},
            }},
            scale: {status: 'SUCCESS', stale: false, source: {name: '天天基金'}, data: {
                shareScale: {value: 0, asOf: '2026-08-31T00:00:00Z'}, combinedAssetScale: null,
            }},
            holdings: {status: 'SUCCESS', stale: true, reportDate: '2026-06-30T00:00:00Z', source: {name: '基金报告'}, data: {
                stockHoldings: [{kind: 'STOCK', code: '600000', name: '浦发银行', weight: 0}],
                industryHoldings: null, regionHoldings: null, currencyHoldings: null,
                disclosedCoverage: 0, lookThrough: true, targetEtfReportDate: '2026-06-30T00:00:00Z',
            }},
        },
    }),
    useFundOpenLots: () => optional.failed ? ({isError: true, refetch: vi.fn()}) : ({
        isLoading: false, isError: false, refetch: vi.fn(),
        data: {
            portfolioFundId: 11, latestNavDate: '2026-09-07T00:00:00Z', latestUnitNav: 1.2345,
            estimatedRedemptionFee: null, unavailableReason: 'REDEMPTION_FEE_MISSING',
            lots: [
                {acquireDate: '2026-08-01T00:00:00Z', holdingDays: 38, remainingShares: 20,
                    redemptionRate: 0, estimatedRedemptionFee: 0, unavailableReason: null},
                {acquireDate: '2026-09-01T00:00:00Z', holdingDays: 7, remainingShares: 10,
                    redemptionRate: null, estimatedRedemptionFee: null, unavailableReason: 'REDEMPTION_FEE_MISSING'},
            ],
        },
    }),
    usePendingTransactions: () => ({data: [{id: 1, fundId: 1, portfolioFundId: 11}, {id: 2, fundId: 1, portfolioFundId: 11}, {id: 3, fundId: 2, portfolioFundId: 12}]}),
    usePendingSignals: () => ({data: [{id: 1, fundId: 1, portfolioFundId: 11}, {id: 2, fundId: 2, portfolioFundId: 12}]}),
}));
vi.mock('./FundTransactionTab.jsx', () => ({default: () => <div>交易流水内容</div>}));
vi.mock('./FundStrategyTab.jsx', () => ({default: () => <div>策略参数内容</div>}));
vi.mock('./FundSignalTab.jsx', () => ({default: ({portfolioFundId}) => <div>纪律建议内容 {portfolioFundId}</div>}));
vi.mock('./FundMarketTab.jsx', () => ({default: () => <div>行情指标内容</div>}));
vi.mock('./FundDcaTab.jsx', () => ({default: () => <div>定投计划内容</div>}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
window.matchMedia = window.matchMedia || (() => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}));
const getComputedStyle = window.getComputedStyle;
window.getComputedStyle = (element) => getComputedStyle(element);
window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
};
const {default: FundDetailPage} = await import('./FundDetailPage.jsx');

describe('FundDetailPage', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        root = null;
        container = null;
        optional.failed = false;
    });

    it('展示当前基金待办、完整费率并提供操作入口', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds/11']}>
                <Routes><Route path="/funds/:portfolioFundId" element={<FundDetailPage/>}/></Routes>
            </MemoryRouter>,
        ));

        expect(container.textContent).toContain('待确认交易 2 笔');
        expect(container.textContent).toContain('待回应建议 1 条');
        expect(container.textContent).toContain('持有不超过 7 天 1.50%');
        expect(container.textContent).toContain('持有超过 7 天 0.00%');
        expect(container.textContent).toContain('销售服务费（年化）');
        expect(container.textContent).toContain('管理费（年化）0.00%');
        expect(container.textContent).toContain('托管费（年化）未爬取');
        expect(container.textContent).toContain('天天基金渠道参考（天天基金）');
        expect(container.textContent).toContain('基金研究');
        expect(container.textContent).toContain('C 类');
        expect(container.textContent).toContain('沪深300ETF（510300）');
        expect(container.textContent).toContain('0 亿份');
        expect(container.textContent).not.toContain('0 亿元');
        expect(container.textContent).toContain('穿透覆盖率0.00%');
        expect(container.textContent).toContain('数据已过期');
        expect(container.textContent).toContain('持仓批次与赎回费估算');
        expect(container.textContent).toContain('38 天');
        expect(container.textContent).toContain('¥0.00');
        expect(container.textContent).toContain('缺少适用赎回费率');
        expect(container.textContent).toContain('总盈亏');
        expect(container.textContent).toContain('-¥20.00');
        expect(container.textContent).toContain('(-20.00%)');
        expect(container.textContent).toContain('今日盈亏');
        expect(container.textContent).toContain('+¥1.50');
        expect(container.textContent).toContain('(+2.00%)');
        expect(container.textContent).not.toContain('今日涨跌');
        expect([...container.querySelectorAll('.ant-tabs-tab-btn')].map((tab) => tab.textContent)).toEqual([
            '行情指标', '交易流水', '策略参数', '纪律建议', '定投计划',
        ]);
        expect(container.querySelector('.ant-tabs-tab-active')?.textContent).toBe('行情指标');
        expect(container.textContent).toContain('行情指标内容');
        await act(async () => [...container.querySelectorAll('.ant-tabs-tab-btn')]
            .find((tab) => tab.textContent === '纪律建议').click());
        expect(container.textContent).toContain('纪律建议内容 11');
        expect([...container.querySelectorAll('a')].map((link) => link.getAttribute('href'))).toEqual(expect.arrayContaining([
            '/confirm?portfolioFundId=11', '/advice?portfolioFundId=11', '/funds?editPortfolioFundId=11',
        ]));
    });

    it('研究、费率和 lot 全部失败时核心详情与业务页签仍可使用', async () => {
        optional.failed = true;
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/funds/11']}>
                <Routes><Route path="/funds/:portfolioFundId" element={<FundDetailPage/>}/></Routes>
            </MemoryRouter>,
        ));

        expect(container.textContent).toContain('测试基金');
        expect(container.textContent).toContain('持仓市值');
        expect(container.textContent).toContain('基金研究数据加载失败');
        expect(container.textContent).toContain('持仓批次加载失败');
        expect(container.textContent).toContain('天天基金渠道参考加载失败');
        expect([...container.querySelectorAll('.ant-tabs-tab-btn')].map((tab) => tab.textContent)).toEqual([
            '行情指标', '交易流水', '策略参数', '纪律建议', '定投计划',
        ]);
        expect(container.textContent).toContain('行情指标内容');
    });
});
