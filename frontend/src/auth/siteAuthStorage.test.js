import {afterEach, describe, expect, it, vi} from 'vitest';
import {
    broadcastSiteLogout,
    isSiteLogoutEvent,
    SITE_LOGOUT_EVENT_KEY,
} from './siteAuthStorage.js';

describe('siteAuthStorage', () => {
    afterEach(() => {
        localStorage.clear();
        vi.restoreAllMocks();
    });

    it('broadcasts logout without storing credentials', () => {
        broadcastSiteLogout();

        expect(localStorage.getItem(SITE_LOGOUT_EVENT_KEY)).toMatch(/^\d+$/);
        expect(Object.keys(localStorage)).toEqual([SITE_LOGOUT_EVENT_KEY]);
    });

    it('ignores storage failures and recognizes only the logout event', () => {
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new DOMException('blocked');
        });

        expect(() => broadcastSiteLogout()).not.toThrow();
        expect(isSiteLogoutEvent({storageArea: localStorage, key: SITE_LOGOUT_EVENT_KEY})).toBe(true);
        expect(isSiteLogoutEvent({storageArea: localStorage, key: 'other'})).toBe(false);
    });
});
