import {describe, expect, it} from 'vitest';
import {calculateMacd, movingAverage, symmetricPercentBound} from './chartMath.js';

describe('chartMath', () => {
    it('returns a symmetric percentage bound with a stable minimum', () => {
        expect(symmetricPercentBound([4, -2, null])).toBe(5);
        expect(symmetricPercentBound([0, 0])).toBe(0.01);
        expect(symmetricPercentBound([0.04])).toBe(0.05);
    });

    it('calculates moving averages with null warm-up values', () => {
        expect(movingAverage([1, 2, 3, 4], 2)).toEqual([null, 1.5, 2.5, 3.5]);
    });

    it('calculates MACD after the EMA warm-up windows', () => {
        const macd = calculateMacd(Array.from({length: 40}, (_, index) => index + 1));
        expect(macd.dif.slice(0, 25).every((value) => value === null)).toBe(true);
        expect(macd.dea.slice(0, 33).every((value) => value === null)).toBe(true);
        expect(Number.isFinite(macd.dif.at(-1))).toBe(true);
        expect(Number.isFinite(macd.dea.at(-1))).toBe(true);
        expect(Number.isFinite(macd.histogram.at(-1))).toBe(true);
    });
});
