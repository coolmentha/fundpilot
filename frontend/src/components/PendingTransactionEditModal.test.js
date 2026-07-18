import {describe, expect, it} from 'vitest';
import dayjs from 'dayjs';
import {canEditPendingTransaction, pendingTransactionBody} from '../transactionEditing.js';

describe('pending transaction editing', () => {
    it('keeps the exact full-holding share string for sell updates', () => {
        expect(pendingTransactionBody('TRANSFER_OUT', {
            shares: '605.37',
            tradeDate: dayjs('2026-07-18'),
        })).toEqual({
            amount: null,
            shares: '605.37',
            tradeDate: '2026-07-18T00:00:00+08:00',
        });
    });


    it('allows every pending primary leg but hides a derived transfer-in leg', () => {
        expect(canEditPendingTransaction({status: 'PENDING', source: 'INVEST'})).toBe(true);
        expect(canEditPendingTransaction({
            status: 'PENDING', source: 'TRANSFER_IN', relatedTransactionId: 2,
        })).toBe(false);
        expect(canEditPendingTransaction({status: 'CONFIRMED', source: 'INCREASE'})).toBe(false);
    });
});
