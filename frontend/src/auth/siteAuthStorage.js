export const SITE_LOGOUT_EVENT_KEY = 'fundpilot.site-logout-at';

export function broadcastSiteLogout() {
    try {
        localStorage.setItem(SITE_LOGOUT_EVENT_KEY, String(Date.now()));
    } catch {
        // The HttpOnly cookie is still cleared even when cross-tab notification is unavailable.
    }
}

export function isSiteLogoutEvent(event) {
    return event?.storageArea === localStorage && event.key === SITE_LOGOUT_EVENT_KEY;
}
