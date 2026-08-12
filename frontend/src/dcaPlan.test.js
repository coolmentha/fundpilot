import {describe, expect, it} from 'vitest';
import {
    canDeleteDcaPlan,
    dcaDecisionReason,
    dcaPlanState,
    dcaScheduleText,
    dcaStrategyText,
} from './dcaPlan.js';

describe('DCA plan display', () => {
    it.each([
        [{frequency: 'DAILY'}, '每个交易日'],
        [{frequency: 'WEEKLY', dayOfWeek: 3}, '周三'],
        [{frequency: 'MONTHLY', dayOfMonth: 15}, '每月15号'],
        [{frequency: 'WEEKLY', dayOfWeek: null}, '-'],
    ])('formats schedule %o', (plan, expected) => {
        expect(dcaScheduleText(plan)).toBe(expected);
    });

    it.each([
        [{status: 'EFFECTIVE', enabled: true}, {label: '运行中', color: 'green'}],
        [{status: 'EFFECTIVE', enabled: false}, {label: '已暂停'}],
        [{status: 'DRAFT', enabled: true}, {label: '已停用'}],
    ])('formats state %o', (plan, expected) => {
        expect(dcaPlanState(plan)).toEqual(expected);
    });

    it('only allows deleting stopped plans', () => {
        expect(canDeleteDcaPlan({status: 'DRAFT'})).toBe(true);
        expect(canDeleteDcaPlan({status: 'EFFECTIVE', enabled: true})).toBe(false);
        expect(canDeleteDcaPlan({status: 'EFFECTIVE', enabled: false})).toBe(false);
    });

    it('formats smart strategies and decision reasons', () => {
        expect(dcaStrategyText('MOVING_AVERAGE')).toBe('均线策略');
        expect(dcaDecisionReason({reasonCode: 'MOVING_AVERAGE'})).toBe('按均线策略执行');
        expect(dcaDecisionReason({reasonCode: 'COST_UNAVAILABLE'})).toBe('平均持仓成本不可用');
        expect(dcaDecisionReason({reasonCode: 'UNKNOWN', reason: '数据不足'})).toBe('数据不足');
    });
});
