import {describe, expect, it} from 'vitest';
import {valuationStatusText} from './valuationStatusText.js';

describe('valuationStatusText', () => {
    it('区分昨日最近净值和今日已确认净值', () => {
        expect(valuationStatusText({
            valuationSource: 'LATEST_CONFIRMED_NAV',
            valuationDate: '2026-07-22T00:00:00Z',
        })).toBe('最近净值 2026-07-22');
        expect(valuationStatusText({valuationSource: 'CONFIRMED_NAV'})).toBe('今日净值已确认');
    });
});
