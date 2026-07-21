import {describe, expect, it} from 'vitest';
import {adjustmentFromTarget} from './transactionEditing.js';

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
