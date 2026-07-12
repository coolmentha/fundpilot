import {describe, expect, it} from 'vitest';
import {date, datetime, errorTitle} from './constants.js';

describe('Instant formatting', () => {
    it('uses Asia/Shanghai for date time', () => {
        expect(datetime('2026-07-09T06:55:00Z')).toBe('2026-07-09 14:55:00');
    });

    it('rolls the date forward in Asia/Shanghai', () => {
        expect(date('2026-07-09T16:30:00Z')).toBe('2026-07-10');
    });

    it('keeps empty and invalid values readable', () => {
        expect(datetime(null)).toBe('-');
        expect(datetime('invalid')).toBe('-');
    });
});

describe('API error titles', () => {
    it('describes admin authentication failures', () => {
        expect(errorTitle('ADMIN_UNAUTHORIZED')).toBe('访问凭据无效');
        expect(errorTitle('ADMIN_AUTH_NOT_CONFIGURED')).toBe('访问鉴权未配置');
    });
});
