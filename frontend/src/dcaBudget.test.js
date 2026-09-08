import {describe, expect, it} from 'vitest';
import {buildDcaBudgetProgress} from './dcaBudget.js';

describe('DCA budget progress', () => {
    it('keeps monthly amounts visible when the optional budget is unset', () => {
        const progress = buildDcaBudgetProgress({
            confirmedInvestedAmount: '500', pendingInvestedAmount: '100', futureAmount: '400',
        });

        expect(progress).toMatchObject({
            hasBudget: false,
            confirmedInvestedAmount: 500,
            pendingInvestedAmount: 100,
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
            confirmedInvestedAmount: 600,
            pendingInvestedAmount: 200,
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
            confirmedInvestedAmount: 700,
            pendingInvestedAmount: 200,
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
            confirmedInvestedAmount: 'invalid',
            pendingInvestedAmount: null,
            futureAmount: null,
        });

        expect(progress.hasBudget).toBe(false);
        expect(progress.projectedAmount).toBe(0);
        expect(Number.isFinite(progress.investedPercent)).toBe(true);
    });

    it('hides the smart range when there are no smart plans', () => {
        const progress = buildDcaBudgetProgress({
            confirmedInvestedAmount: 100,
            pendingInvestedAmount: 0,
            futureAmount: 200,
            minimumFutureAmount: null,
            maximumFutureAmount: null,
        });

        expect(progress.minimumFutureAmount).toBeNull();
        expect(progress.maximumFutureAmount).toBeNull();
    });

    it('keeps each future plan maximum distinct from a missing amount', () => {
        const progress = buildDcaBudgetProgress({
            confirmedInvestedAmount: 100,
            pendingInvestedAmount: 20,
            futureAmount: 300,
            futurePlans: [
                {planId: 1, amount: 100, maximumAmount: 180},
                {planId: 2, amount: 'invalid', maximumAmount: 0},
            ],
        });

        expect(progress.futurePlans).toEqual([
            {planId: 1, amount: 100, maximumAmount: 180},
            {planId: 2, amount: null, maximumAmount: 0},
        ]);
    });
});
