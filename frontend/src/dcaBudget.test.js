import {describe, expect, it} from 'vitest';
import {buildDcaBudgetProgress} from './dcaBudget.js';

describe('DCA budget progress', () => {
    it('keeps monthly amounts visible when the optional budget is unset', () => {
        const progress = buildDcaBudgetProgress({investedAmount: '600', futureAmount: '400'});

        expect(progress).toMatchObject({
            hasBudget: false,
            investedAmount: 600,
            futureAmount: 400,
            projectedAmount: 1000,
            remainingAmount: null,
            overBudgetAmount: null,
        });
    });

    it('splits invested and future amounts within a remaining budget', () => {
        const progress = buildDcaBudgetProgress({
            monthlyBudget: 2000,
            investedAmount: 800,
            futureAmount: 400,
        });

        expect(progress).toMatchObject({
            hasBudget: true,
            projectedAmount: 1200,
            remainingAmount: 800,
            overBudgetAmount: 0,
            investedPercent: 40,
            futurePercent: 20,
            budgetPercent: 100,
            isOverBudget: false,
        });
    });

    it('uses the projected total as scale and marks the excess when over budget', () => {
        const progress = buildDcaBudgetProgress({
            monthlyBudget: 1000,
            investedAmount: 900,
            futureAmount: 300,
        });

        expect(progress.investedPercent).toBeCloseTo(75);
        expect(progress.futurePercent).toBeCloseTo(25);
        expect(progress.budgetPercent).toBeCloseTo(83.333, 2);
        expect(progress.overBudgetAmount).toBe(200);
        expect(progress.isOverBudget).toBe(true);
    });

    it('does not produce invalid progress from missing or malformed amounts', () => {
        const progress = buildDcaBudgetProgress({
            monthlyBudget: 0,
            investedAmount: 'invalid',
            futureAmount: null,
        });

        expect(progress.hasBudget).toBe(false);
        expect(progress.projectedAmount).toBe(0);
        expect(Number.isFinite(progress.investedPercent)).toBe(true);
    });
});
