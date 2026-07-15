import {describe, expect, it} from 'vitest';
import {dcaScheduleText} from './dcaPlan.js';

describe('DCA plan display', () => {
    it.each([
        [{frequency: 'DAILY'}, '每个交易日'],
        [{frequency: 'WEEKLY', dayOfWeek: 3}, '周三'],
        [{frequency: 'MONTHLY', dayOfMonth: 15}, '每月15号'],
        [{frequency: 'WEEKLY', dayOfWeek: null}, '-'],
    ])('formats schedule %o', (plan, expected) => {
        expect(dcaScheduleText(plan)).toBe(expected);
    });
});
