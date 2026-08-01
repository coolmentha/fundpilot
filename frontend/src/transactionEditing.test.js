import {describe, expect, it} from 'vitest';
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc.js';
import {adjustmentFromTarget, pendingTransactionBody} from './transactionEditing.js';

dayjs.extend(utc);

describe('adjustmentFromTarget', () => {
    it('根据目标持仓生成调增差额', () => {
        expect(adjustmentFromTarget(12.5, 20)).toEqual({source: 'ADJUST_IN', shares: 7.5});
    });

    it('根据目标持仓生成调减差额', () => {
        expect(adjustmentFromTarget(20, 12.5)).toEqual({source: 'ADJUST_OUT', shares: 7.5});
    });

    it('目标等于当前持仓时不创建调整', () => {
        expect(adjustmentFromTarget(12.5, 12.5)).toBeNull();
    });
});

describe('pendingTransactionBody', () => {
    it('非北京时区浏览器按北京时间回传交易日期', () => {
        const values = {amount: 1000, shares: null, tradeDate: dayjs('2026-07-31T16:00:00.000Z')};
        expect(pendingTransactionBody('INCREASE', values)).toEqual({
            amount: 1000,
            shares: null,
            tradeDate: '2026-08-01T00:00:00+08:00',
        });
    });

    it('北京时区浏览器日期不变', () => {
        const values = {amount: 1000, shares: null, tradeDate: dayjs('2026-08-01T00:00:00.000Z')};
        expect(pendingTransactionBody('INCREASE', values)).toEqual({
            amount: 1000,
            shares: null,
            tradeDate: '2026-08-01T00:00:00+08:00',
        });
    });
});
