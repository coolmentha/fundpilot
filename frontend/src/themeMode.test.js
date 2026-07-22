import {describe, expect, it} from 'vitest';
import {readTheme, THEME_STORAGE_KEY} from './themeMode.js';

describe('readTheme', () => {
    it.each([
        ['light', 'light'],
        ['dark', 'dark'],
        ['unknown', 'dark'],
        [null, 'dark'],
    ])('maps stored value %s to %s', (stored, expected) => {
        const storage = {getItem: (key) => key === THEME_STORAGE_KEY ? stored : null};
        expect(readTheme(storage)).toBe(expected);
    });

    it('falls back to dark when storage is unavailable', () => {
        expect(readTheme({getItem: () => { throw new Error('blocked'); }})).toBe('dark');
    });
});
