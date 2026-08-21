import {describe, expect, it} from 'vitest';
import {
    date, datetime, errorTitle, fundSourceOptions, percent, text, transactionCostPerShare,
} from './constants.js';

describe('Instant formatting', () => {
    it('uses Asia/Shanghai for date time', () => {
        expect(datetime('2026-07-09T06:55:00Z')).toBe('2026-07-09 14:55:00');
    });

    it('rolls the date forward in Asia/Shanghai', () => {
        expect(date('2026-07-09T16:30:00Z')).toBe('2026-07-10');
    });

    it('keeps empty and invalid values readable', () => {
        expect(datetime(null)).toBe('-');
        expect(datetime('invalid')).toBe('-');
    });
});

describe('API error titles', () => {
    it('describes admin authentication failures', () => {
        expect(errorTitle('ADMIN_UNAUTHORIZED')).toBe('访问凭据无效');
        expect(errorTitle('ADMIN_AUTH_NOT_CONFIGURED')).toBe('访问鉴权未配置');
    });

    it('covers transaction and onboarding business errors', () => {
        expect(errorTitle('OPENED_AT_IN_FUTURE')).toBe('建仓时间晚于当前时间');
        expect(errorTitle('INSUFFICIENT_LOTS')).toBe('可用持仓批次不足');
        expect(errorTitle('FUND_HAS_PENDING_TRANSACTIONS')).toBe('基金存在待确认交易');
        expect(errorTitle('MONTHLY_DCA_BUDGET_INVALID')).toBe('每月定投预算不合法');
        expect(errorTitle('POSITION_WARNING_RATIO_INVALID')).toBe('仓位提醒线不合法');
        expect(errorTitle('DCA_PLAN_DELETE_REQUIRES_DRAFT')).toBe('请先停用定投计划');
    });
});

describe('numeric formatting', () => {
    it('does not render missing percentages as zero', () => {
        expect(percent(null)).toBe('-');
        expect(percent(undefined)).toBe('-');
        expect(percent(0)).toBe('0.00%');
    });
});

describe('transaction cost basis', () => {
    it('labels cost corrections without exposing them as manual transactions', () => {
        expect(text('COST_BASIS_RESET')).toBe('成本修正');
        expect(fundSourceOptions.map(({value}) => value)).not.toContain('COST_BASIS_RESET');
    });

    it('derives cost per share only for cost corrections', () => {
        expect(transactionCostPerShare({
            source: 'COST_BASIS_RESET', amount: '120.00', shares: '100.00',
        })).toBe(1.2);
        expect(transactionCostPerShare({
            source: 'INCREASE', amount: '120.00', shares: '100.00',
        })).toBeNull();
    });

    it.each([
        {source: 'COST_BASIS_RESET', amount: '120.00', shares: '0'},
        {source: 'COST_BASIS_RESET', amount: null, shares: '100'},
        {source: 'COST_BASIS_RESET', amount: 'not-a-number', shares: '100'},
        {source: 'COST_BASIS_RESET', amount: '120.00', shares: null},
        {source: 'COST_BASIS_RESET', amount: '120.00', shares: 'not-a-number'},
        null,
    ])('returns no value for invalid correction data %#', (transaction) => {
        expect(transactionCostPerShare(transaction)).toBeNull();
    });
});
