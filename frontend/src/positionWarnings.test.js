import {describe, expect, it} from 'vitest';
import {buildFundPositionWarnings} from './positionWarnings.js';

describe('fund position warnings', () => {
    it('calculates each fund from the total confirmed holding market value', () => {
        const rows = buildFundPositionWarnings([
            {id: 1, holdingAmount: 1500, positionWarningEnabled: true, positionWarningRatio: 0.7},
            {id: 2, holdingAmount: 500, positionWarningEnabled: true, positionWarningRatio: 0.3},
        ]);

        expect(rows[0].positionRatio).toBeCloseTo(0.75);
        expect(rows[0].positionWarningExceeded).toBe(true);
        expect(rows[1].positionRatio).toBeCloseTo(0.25);
        expect(rows[1].positionWarningExceeded).toBe(false);
    });

    it('keeps the ratio visible but suppresses the alert when a fund turns reminders off', () => {
        const [row] = buildFundPositionWarnings([
            {id: 1, holdingAmount: 1000, positionWarningEnabled: false, positionWarningRatio: 0.3},
        ]);

        expect(row.positionRatio).toBe(1);
        expect(row.positionWarningExceeded).toBe(false);
    });

    it('does not calculate partial ratios when any confirmed holding market value is unavailable', () => {
        const rows = buildFundPositionWarnings([
            {id: 1, holdingShares: 100, holdingAmount: null},
            {id: 2, holdingShares: 200, holdingAmount: 1000},
        ]);

        expect(rows[0].positionRatio).toBeNull();
        expect(rows[1].positionRatio).toBeNull();
    });
});
