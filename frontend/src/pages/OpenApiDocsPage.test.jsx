import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('../api/openApiToken.js', () => ({
    useOpenApiToken: () => ({data: {active: false}, isError: false, refetch: vi.fn()}),
    useRotateOpenApiToken: () => ({mutateAsync: vi.fn().mockResolvedValue({token: 'fp_test_token'}), isPending: false}),
}));

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

const {default: OpenApiDocsPage} = await import('./OpenApiDocsPage.jsx');

describe('OpenApiDocsPage', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        root = null;
        container = null;
    });

    it('展示 Bearer 鉴权说明与全部只读资源', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<OpenApiDocsPage/>));

        expect(container.textContent).toContain('Open API');
        expect(container.textContent).toContain('Authorization: Bearer');
        expect(container.textContent).toContain('创建明文只显示一次');
        expect(container.textContent).toContain('刷新立即轮换');
        expect(container.textContent).toContain('/api/open/v1/market/status');
        expect(container.textContent).toContain('/api/open/v1/alerts/notifications');
    });
});
