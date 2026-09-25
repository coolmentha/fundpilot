import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
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

const {default: HelpPage} = await import('./HelpPage.jsx');

describe('HelpPage', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
    });

    const render = async (element = <HelpPage/>) => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<MemoryRouter>{element}</MemoryRouter>));
    };

    it('展示引导标题、三步配置与六个按页面入口', async () => {
        await render();

        expect(container.textContent).toContain('从这里开始使用');
        expect(container.textContent).toContain('三步完成第一次配置');
        expect(container.textContent).toContain('按页面查找');

        const steps = [...container.querySelectorAll('.help-step')];
        expect(steps).toHaveLength(3);
        expect(steps.map((step) => step.getAttribute('href'))).toEqual(['/funds', '/dca', '/confirm']);
        expect(steps.map((step) => step.querySelector('strong').textContent))
            .toEqual(['添加基金', '设置计划', '处理确认']);

        const destinations = [...container.querySelectorAll('.help-destination')];
        expect(destinations).toHaveLength(6);
        expect(destinations.map((item) => item.getAttribute('href')))
            .toEqual(['/', '/funds', '/dca', '/alerts', '/confirm', '/settings']);
        expect(container.textContent).toContain('行情工作台');
        expect(container.textContent).toContain('用户配置');
    });

    it('渲染四条操作说明与各自的完成标志', async () => {
        await render();

        expect(container.textContent).toContain('常用操作说明');
        const workflows = [...container.querySelectorAll('.help-workflow')];
        expect(workflows).toHaveLength(4);
        expect(workflows.map((workflow) => workflow.querySelector('h5').textContent))
            .toEqual(['添加基金与维护持仓', '设置自动定投', '配置价格提醒', '处理待确认交易']);
        expect(workflows.map((workflow) => workflow.querySelectorAll('.help-workflow-steps li').length))
            .toEqual([3, 3, 3, 3]);
        expect(workflows[3].querySelector('footer').textContent).toContain('完成标志');
        expect(workflows[3].querySelector('footer').textContent).toContain('CONFIRMED');
        expect(workflows[0].querySelector('footer a').getAttribute('href')).toBe('/funds');
        expect(container.textContent).toContain('盘中估值');
        expect(container.textContent).toContain('累计净值');
    });

    it('底部按钮指向操作说明锚点与行情工作台', async () => {
        await render();

        expect(container.textContent).toContain('系统负责计算、提醒、生成待确认流水和记账');
        const actions = [...container.querySelectorAll('.help-footer-actions a')];
        expect(actions.map((action) => action.getAttribute('href'))).toEqual(['#help-detail-title', '/']);
        expect(actions.map((action) => action.textContent)).toEqual(['查看操作说明', '返回行情工作台']);
    });

    it('点击入口链接跳转到对应页面', async () => {
        await render(
            <Routes>
                <Route path="/" element={<HelpPage/>}/>
                <Route path="/dca" element={<div>定投管理目的地</div>}/>
            </Routes>,
        );

        const dcaLink = [...container.querySelectorAll('.help-destination')]
            .find((item) => item.getAttribute('href') === '/dca');
        await act(async () => dcaLink.click());

        expect(container.textContent).toContain('定投管理目的地');
    });
});