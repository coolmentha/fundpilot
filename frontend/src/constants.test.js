import {describe, expect, it} from 'vitest';
import {date, datetime} from './constants.js';

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
