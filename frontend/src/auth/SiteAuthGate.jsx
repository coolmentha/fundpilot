import React, {useCallback, useEffect, useState} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import LoginPage from '../pages/LoginPage.jsx';
import {
    clearSiteApiKey,
    setSiteApiKey,
    setSiteUnauthorizedHandler,
    verifySiteApiKey,
} from '../api/client.js';
import {SiteAuthContext} from './SiteAuthContext.js';

export default function SiteAuthGate({children}) {
    const queryClient = useQueryClient();
    const [authenticated, setAuthenticated] = useState(false);

    const logout = useCallback(() => {
        clearSiteApiKey();
        queryClient.clear();
        setAuthenticated(false);
    }, [queryClient]);

    useEffect(() => {
        if (!authenticated) return undefined;
        setSiteUnauthorizedHandler(logout);
        return () => setSiteUnauthorizedHandler(null);
    }, [authenticated, logout]);

    const login = async (apiKey) => {
        await verifySiteApiKey(apiKey);
        setSiteApiKey(apiKey);
        queryClient.clear();
        setAuthenticated(true);
    };

    if (!authenticated) return React.createElement(LoginPage, {onLogin: login});

    return React.createElement(SiteAuthContext.Provider, {value: {logout}}, children);
}
