import {describe, expect, it} from 'vitest';
import {redemptionLadderText, redemptionTierHoldingPeriod} from './feeRates.js';

describe('赎回费率展示', () => {
    const ladder = [
        {maxDays: 7, rate: 0.015},
        {maxDays: 30, rate: 0.005},
        {maxDays: null, rate: 0},
    ];

    it('按持有期显示完整且无歧义的赎回费率阶梯', () => {
        expect(redemptionTierHoldingPeriod(ladder[0], 0, ladder)).toBe('持有不超过 7 天');
        expect(redemptionTierHoldingPeriod(ladder[1], 1, ladder)).toBe('持有超过 7 天且不超过 30 天');
        expect(redemptionTierHoldingPeriod(ladder[2], 2, ladder)).toBe('持有超过 30 天');
        expect(redemptionLadderText(ladder)).toBe('持有不超过 7 天 1.50%；持有超过 7 天且不超过 30 天 0.50%；持有超过 30 天 0.00%');
    });

    it('未提供赎回费率时返回空值', () => {
        expect(redemptionLadderText([])).toBeNull();
        expect(redemptionLadderText(null)).toBeNull();
    });
});
