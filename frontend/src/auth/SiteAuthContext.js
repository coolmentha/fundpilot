import {createContext, useContext} from 'react';

export const SiteAuthContext = createContext(null);

export function useSiteAuth() {
    const context = useContext(SiteAuthContext);
    if (!context) throw new Error('useSiteAuth must be used inside SiteAuthGate');
    return context;
}
