import {createContext} from 'react';

export const THEME_STORAGE_KEY = 'fundpilot-theme';

export const ThemeModeContext = createContext({
    themeMode: 'dark',
    toggleTheme: () => {},
});

export function readTheme(storage = globalThis.localStorage) {
    try {
        return storage.getItem(THEME_STORAGE_KEY) === 'light' ? 'light' : 'dark';
    } catch {
        return 'dark';
    }
}
