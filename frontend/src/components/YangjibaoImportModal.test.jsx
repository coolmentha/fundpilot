import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, describe, expect, it, vi} from 'vitest';

const api = vi.hoisted(() => ({
    cancelYangjibaoSession: vi.fn(() => Promise.resolve()),
    createYangjibaoSession: vi.fn(() => Promise.resolve({
        sessionId: 'new-session', status: 'WAITING', qrUrl: 'https://example.test/qr',
    })),
    getYangjibaoImportStatus: vi.fn(() => Promise.resolve({
        status: 'PROCESSING', total: 1, processed: 0, succeeded: 0, failed: 0,
        currentFund: null, results: [],
    })),
    getYangjibaoPreview: vi.fn(() => Promise.resolve([])),
    getYangjibaoSession: vi.fn(() => Promise.resolve({
        sessionId: 'processing-session', status: 'PROCESSING', qrUrl: null,
    })),
    getYangjibaoSessions: vi.fn(() => Promise.resolve([])),
    retryYangjibaoImport: vi.fn(() => Promise.resolve()),
    useRunYangjibaoImport: vi.fn(() => ({mutateAsync: vi.fn(), isPending: false})),
}));

vi.mock('../api/hooks.js', () => api);
vi.mock('antd', async () => {
    const React = await import('react');
    const passthrough = ({children}) => React.createElement('div', null, children);
    const Button = ({children, icon, loading: _loading, type: _type, ...props}) => React.createElement('button', props, icon, children);
    const Modal = ({open, title, footer, children}) => open
        ? React.createElement('section', null, title, children, footer)
        : null;
    const Table = ({dataSource = [], columns = []}) => React.createElement('div', null,
        dataSource.map(row => React.createElement('div', {key: row.itemId},
            columns.map((column, index) => React.createElement('span', {key: column.dataIndex || index},
                column.render ? column.render(row[column.dataIndex], row) : row[column.dataIndex])))));
    const Typography = {
        Text: ({children}) => React.createElement('span', null, children),
        Title: ({children}) => React.createElement('h4', null, children),
    };
    return {
        Alert: passthrough,
        Button,
        Checkbox: Button,
        Input: passthrough,
        Modal,
        Progress: passthrough,
        QRCode: passthrough,
        Radio: {Group: passthrough},
        Segmented: passthrough,
        Space: passthrough,
        Spin: passthrough,
        Steps: passthrough,
        Table,
        Tag: passthrough,
        Typography,
    };
});

import YangjibaoImportModal from './YangjibaoImportModal.jsx';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.React = React;

const processingSession = {
    sessionId: 'processing-session', status: 'PROCESSING', updatedAt: '2026-09-07T01:02:03Z',
    total: 2, processed: 1, succeeded: 1, failed: 0,
};
const completedSession = {
    sessionId: 'completed-session', status: 'COMPLETED', updatedAt: '2026-09-06T01:02:03Z',
    total: 2, processed: 2, succeeded: 2, failed: 0,
};

describe('YangjibaoImportModal recovery', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
        document.body.innerHTML = '';
        root = null;
        container = null;
        vi.clearAllMocks();
    });

    async function renderModal(onClose = vi.fn()) {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        await act(async () => root.render(<YangjibaoImportModal open onClose={onClose}/>));
        await act(async () => new Promise(resolve => setTimeout(resolve, 0)));
        return onClose;
    }

    it('lists recoverable sessions before offering explicit new import', async () => {
        api.getYangjibaoSessions.mockResolvedValue([processingSession, completedSession]);

        await renderModal();

        expect(api.getYangjibaoSessions).toHaveBeenCalledTimes(1);
        expect(api.createYangjibaoSession).not.toHaveBeenCalled();
        expect(container.textContent).toContain('进行中的导入');
        expect(container.textContent).toContain('已完成的导入');
        expect(container.textContent).toContain('新建导入');
    });

    it('creates a session only after the explicit new-import action', async () => {
        api.getYangjibaoSessions.mockResolvedValue([]);
        api.createYangjibaoSession.mockResolvedValue({
            sessionId: 'new-session', status: 'WAITING', qrUrl: 'https://example.test/qr',
        });

        await renderModal();
        const create = [...container.querySelectorAll('button')]
            .find(button => button.textContent === '新建导入');
        await act(async () => create.click());

        expect(api.createYangjibaoSession).toHaveBeenCalledTimes(1);
        expect(container.textContent).toContain('连接养基宝账户');
    });

    it('restores the selected processing job from its persisted status', async () => {
        api.getYangjibaoSessions.mockResolvedValue([processingSession]);
        api.getYangjibaoSession.mockResolvedValue({
            sessionId: processingSession.sessionId, status: 'PROCESSING', qrUrl: null,
        });
        api.getYangjibaoImportStatus.mockResolvedValue({
            status: 'PROCESSING', total: 2, processed: 1, succeeded: 1, failed: 0,
            currentFund: null, results: [],
        });

        await renderModal();
        const restore = [...container.querySelectorAll('button')]
            .find(button => button.textContent === '恢复');
        await act(async () => restore.click());

        expect(api.getYangjibaoSession).toHaveBeenCalledWith(processingSession.sessionId);
        expect(api.getYangjibaoImportStatus).toHaveBeenCalledWith(processingSession.sessionId);
        expect(container.textContent).toContain('正在同步持仓');
    });

    it('can recover the same completed job after remounting', async () => {
        api.getYangjibaoSessions.mockResolvedValue([completedSession]);
        api.getYangjibaoSession.mockResolvedValue({
            sessionId: completedSession.sessionId, status: 'COMPLETED', qrUrl: null,
        });
        api.getYangjibaoImportStatus.mockResolvedValue({
            status: 'COMPLETED', total: 2, processed: 2, succeeded: 2, failed: 0,
            currentFund: null, results: [],
        });

        await renderModal();
        let restore = [...container.querySelectorAll('button')]
            .find(button => button.textContent === '恢复');
        await act(async () => restore.click());
        expect(container.textContent).toContain('导入完成');

        await act(async () => root.unmount());
        container.remove();
        container = null;
        root = null;

        await renderModal();
        restore = [...container.querySelectorAll('button')]
            .find(button => button.textContent === '恢复');
        await act(async () => restore.click());

        expect(api.getYangjibaoSessions).toHaveBeenCalledTimes(2);
        expect(api.getYangjibaoImportStatus).toHaveBeenCalledTimes(2);
        expect(container.textContent).toContain('导入完成');
    });

    it('keeps a processing task when the modal is closed and reopened', async () => {
        api.getYangjibaoSessions.mockResolvedValue([processingSession]);
        api.getYangjibaoSession.mockResolvedValue({
            sessionId: processingSession.sessionId, status: 'PROCESSING', qrUrl: null,
        });
        api.getYangjibaoImportStatus.mockResolvedValue({
            status: 'PROCESSING', total: 2, processed: 1, succeeded: 1, failed: 0,
            currentFund: null, results: [],
        });
        const onClose = vi.fn();

        await renderModal(onClose);
        const restore = [...container.querySelectorAll('button')]
            .find(button => button.textContent === '恢复');
        await act(async () => restore.click());
        const continueInBackground = [...container.querySelectorAll('button')]
            .find(button => button.textContent === '后台继续');
        await act(async () => continueInBackground.click());
        expect(api.cancelYangjibaoSession).not.toHaveBeenCalled();
        expect(onClose).toHaveBeenCalledTimes(1);

        await act(async () => root.render(<YangjibaoImportModal open={false} onClose={onClose}/>));
        await act(async () => root.render(<YangjibaoImportModal open onClose={onClose}/>));
        await act(async () => new Promise(resolve => setTimeout(resolve, 0)));

        expect(api.getYangjibaoSessions).toHaveBeenCalledTimes(2);
    });

    it('shows safe failure details and retries only through the failed-item endpoint', async () => {
        api.getYangjibaoSessions.mockResolvedValue([{...completedSession, failed: 1, succeeded: 1}]);
        api.getYangjibaoSession.mockResolvedValue({
            sessionId: completedSession.sessionId, status: 'COMPLETED', qrUrl: null,
        });
        api.getYangjibaoImportStatus.mockResolvedValue({
            status: 'COMPLETED', total: 2, processed: 2, succeeded: 1, failed: 1,
            currentFund: null,
            results: [
                {
                    itemId: 'a:h1', fundCode: '000001', status: 'CREATED',
                    failureCode: null, message: '导入成功', correlationId: null,
                },
                {
                    itemId: 'a:h2', fundCode: '017094', status: 'FAILED',
                    failureCode: 'IMPORT_DEPENDENCY_FAILED',
                    message: '暂时无法完成导入，请稍后重试', correlationId: 'imp-003',
                },
            ],
        });
        api.retryYangjibaoImport.mockResolvedValue({
            status: 'PROCESSING', total: 2, processed: 1, succeeded: 1, failed: 0,
            currentFund: '017094', results: [],
        });

        await renderModal();
        const restore = [...container.querySelectorAll('button')]
            .find(button => button.textContent === '恢复');
        await act(async () => restore.click());

        expect(container.textContent).toContain('IMPORT_DEPENDENCY_FAILED');
        expect(container.textContent).toContain('暂时无法完成导入，请稍后重试');
        expect(container.textContent).toContain('imp-003');
        const successfulRow = [...container.querySelectorAll('span')]
            .find(cell => cell.textContent === '000001').parentElement;
        expect(successfulRow.textContent).toContain('CREATED');
        expect(successfulRow.textContent).not.toContain('IMPORT_DEPENDENCY_FAILED');
        expect(successfulRow.textContent).not.toContain('imp-003');
        const retry = [...container.querySelectorAll('button')]
            .find(button => button.textContent.includes('仅重试失败项'));
        await act(async () => retry.click());
        expect(api.retryYangjibaoImport).toHaveBeenCalledWith(completedSession.sessionId);
    });
});
