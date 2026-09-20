import React, {act} from 'react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {createRoot} from 'react-dom/client';

const {useSectorPerformance} = vi.hoisted(() => ({useSectorPerformance: vi.fn()}));
vi.mock('../api/hooks.js', () => ({useSectorPerformance}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;
// antd Table 的响应式断点需要 matchMedia,jsdom 未实现;恒为 false 即按桌面断点渲染。
window.matchMedia = window.matchMedia || (() => ({
    matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(),
}));
globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };

const {default: SectorPerformance} = await import('./SectorPerformance.jsx');

const SECTORS = [
    {sectorCode: 'BK1', sectorName: '半导体', changePct: 0.02, turnover: 1000, mainforceNet: 100},
    {sectorCode: 'BK2', sectorName: '涂料', changePct: 0.01, turnover: 500, mainforceNet: 250},
    {sectorCode: 'BK3', sectorName: '种子', changePct: -0.015, turnover: 2000, mainforceNet: -300},
    {sectorCode: 'BK4', sectorName: '汽车服务', changePct: -0.011, turnover: 800, mainforceNet: -7},
    {sectorCode: 'BK5', sectorName: '学历教育', changePct: -0.01, turnover: 600, mainforceNet: 0},
    {sectorCode: 'BK6', sectorName: '其他', changePct: 0, turnover: 400, mainforceNet: null},
];

describe('SectorPerformance', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        vi.clearAllMocks();
    });

    it('方向选项带全量条数，零值与缺失值不计入任何方向', async () => {
        useSectorPerformance.mockReturnValue({data: SECTORS, isLoading: false, isError: false, refetch: vi.fn()});
        await render();

        expect(optionLabels()).toEqual(expect.arrayContaining(['净流入 2', '净流出 2']));
    });

    it('切到净流出只留净流出行业，切回全部恢复全量', async () => {
        useSectorPerformance.mockReturnValue({data: SECTORS, isLoading: false, isError: false, refetch: vi.fn()});
        await render();

        // 默认排序仍是涨跌幅:汽车服务 -1.1% 排在种子 -1.5% 之前
        await clickOption('净流出 2');
        expect(rowNames()).toEqual(['汽车服务', '种子']);

        await clickOption('全部');
        expect(rowNames()).toHaveLength(6);
    });

    it('净流入视角下净额降序让流入最大的行业排最前', async () => {
        useSectorPerformance.mockReturnValue({data: SECTORS, isLoading: false, isError: false, refetch: vi.fn()});
        await render();

        await clickOption('净流入 2');
        await clickOption('主力净额');
        expect(rowNames()).toEqual(['涂料', '半导体']);
    });

    it('净流出视角把净额排序标签改为净流出额并让流出最大的行业排最前', async () => {
        useSectorPerformance.mockReturnValue({data: SECTORS, isLoading: false, isError: false, refetch: vi.fn()});
        await render();

        await clickOption('净流出 2');
        await clickOption('主力净额');
        // 选中后标签改为净流出额,说明排序口径是流出额从大到小
        expect(optionLabels()).toContain('净流出额');
        expect(rowNames()).toEqual(['种子', '汽车服务']);
    });

    it('筛选后无匹配行业时显示对应空态文案', async () => {
        useSectorPerformance.mockReturnValue({
            data: SECTORS.filter((sector) => Number(sector.mainforceNet) > 0),
            isLoading: false, isError: false, refetch: vi.fn(),
        });
        await render();

        await clickOption('净流出 0');
        expect(rowNames()).toEqual([]);
        expect(container.textContent).toContain('暂无主力净流出行业');
    });

    function optionLabels() {
        return [...container.querySelectorAll('.ant-segmented-item')].map((item) => item.textContent);
    }

    function rowNames() {
        return [...container.querySelectorAll('.ant-table-tbody tr:not([aria-hidden="true"]):not(.ant-table-placeholder)')]
            .map((row) => row.querySelector('td')?.textContent);
    }

    async function clickOption(text) {
        const item = [...container.querySelectorAll('.ant-segmented-item')]
            .find((candidate) => candidate.textContent === text);
        expect(item, `未找到选项 ${text}`).toBeTruthy();
        await act(async () => item.click());
    }

    async function render() {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<SectorPerformance/>));
    }
});
