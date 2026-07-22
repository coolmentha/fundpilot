import React, {useCallback, useEffect, useState} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {Button, Result, Spin} from 'antd';
import LoginPage from '../pages/LoginPage.jsx';
import {
    loginSiteApiKey,
    logoutSiteSession,
    markSiteAuthChanged,
    setSiteUnauthorizedHandler,
    verifySiteSession,
} from '../api/client.js';
import {SiteAuthContext} from './SiteAuthContext.js';
import {
    broadcastSiteLogout,
    isSiteLogoutEvent,
} from './siteAuthStorage.js';

export default function SiteAuthGate({children}) {
    const queryClient = useQueryClient();
    const [restoring, setRestoring] = useState(true);
    const [restoreAttempt, setRestoreAttempt] = useState(0);
    const [restoreError, setRestoreError] = useState(null);
    const [authenticated, setAuthenticated] = useState(false);
    const [user, setUser] = useState(null);

    const clearAuthenticatedState = useCallback(() => {
        markSiteAuthChanged();
        queryClient.clear();
        setAuthenticated(false);
        setUser(null);
    }, [queryClient]);

    const handleUnauthorized = useCallback(() => {
        clearAuthenticatedState();
        broadcastSiteLogout();
    }, [clearAuthenticatedState]);

    const logout = useCallback(async () => {
        try {
            await logoutSiteSession();
        } finally {
            clearAuthenticatedState();
            broadcastSiteLogout();
        }
    }, [clearAuthenticatedState]);

    useEffect(() => {
        if (!authenticated) return undefined;
        setSiteUnauthorizedHandler(handleUnauthorized);
        return () => setSiteUnauthorizedHandler(null);
    }, [authenticated, handleUnauthorized]);

    useEffect(() => {
        let active = true;

        verifySiteSession()
            .then((currentUser) => {
                if (!active) return;
                markSiteAuthChanged();
                queryClient.clear();
                setAuthenticated(true);
                setUser(currentUser);
            })
            .catch((error) => {
                if (!active) return;
                if (error?.code === 'ADMIN_UNAUTHORIZED' || error?.code === 'HTTP_401') {
                    clearAuthenticatedState();
                    return;
                }
                setRestoreError(error);
            })
            .finally(() => {
                if (active) setRestoring(false);
            });

        return () => {
            active = false;
        };
    }, [clearAuthenticatedState, queryClient, restoreAttempt]);

    useEffect(() => {
        const handleStorage = (event) => {
            if (isSiteLogoutEvent(event)) {
                clearAuthenticatedState();
            }
        };
        window.addEventListener('storage', handleStorage);
        return () => window.removeEventListener('storage', handleStorage);
    }, [clearAuthenticatedState]);

    const retryRestore = () => {
        setRestoreError(null);
        setRestoring(true);
        setRestoreAttempt((attempt) => attempt + 1);
    };

    const abandonRestore = async () => {
        await logout();
        setRestoreError(null);
    };

    const login = async (credentials) => {
        const currentUser = await loginSiteApiKey(credentials);
        markSiteAuthChanged();
        queryClient.clear();
        setAuthenticated(true);
        setUser(currentUser);
    };

    if (restoring) {
        return React.createElement(
            'main',
            {className: 'site-login-page', 'aria-label': '正在验证登录状态'},
            React.createElement(Spin, {size: 'large'}),
        );
    }
    if (restoreError) {
        return React.createElement(
            'main',
            {className: 'site-login-page', 'aria-label': '登录状态验证失败'},
            React.createElement(Result, {
                status: 'warning',
                title: '暂时无法验证登录状态',
                extra: React.createElement(
                    React.Fragment,
                    null,
                    React.createElement(Button, {type: 'primary', onClick: retryRestore}, '重试'),
                    React.createElement(Button, {onClick: abandonRestore}, '重新登录'),
                ),
            }),
        );
    }
    if (!authenticated) return React.createElement(LoginPage, {onLogin: login});

    return React.createElement(SiteAuthContext.Provider, {value: {logout, user}}, children);
}
