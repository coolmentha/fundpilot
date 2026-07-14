import {describe, expect, it} from 'vitest';
import {date, datetime, errorTitle, percent} from './constants.js';

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

    it('covers transaction and onboarding business errors', () => {
        expect(errorTitle('OPENED_AT_IN_FUTURE')).toBe('建仓时间晚于当前时间');
        expect(errorTitle('INSUFFICIENT_LOTS')).toBe('可用持仓批次不足');
        expect(errorTitle('FUND_HAS_PENDING_TRANSACTIONS')).toBe('基金存在待确认交易');
    });
});

describe('numeric formatting', () => {
    it('does not render missing percentages as zero', () => {
        expect(percent(null)).toBe('-');
        expect(percent(undefined)).toBe('-');
        expect(percent(0)).toBe('0.00%');
    });
});
