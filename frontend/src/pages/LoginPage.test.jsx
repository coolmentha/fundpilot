import React, {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, describe, expect, it, vi} from 'vitest';
import LoginPage from './LoginPage.jsx';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

describe('LoginPage', () => {
    let container;
    let root;

    afterEach(async () => {
        if (root) await act(async () => root.unmount());
        container?.remove();
    });

    it('uses a text field for username and a password field for password', async () => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);

        await act(async () => root.render(React.createElement(LoginPage, {onLogin: vi.fn()})));

        expect(container.querySelector('input[autocomplete="username"]')?.type).toBe('text');
        expect(container.querySelector('input[autocomplete="current-password"]')?.type).toBe('password');
        expect(container.textContent).not.toContain('访问 Key');
    });
});
