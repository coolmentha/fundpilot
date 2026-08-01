import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc.js';

dayjs.extend(utc);

const BUY_SOURCES = new Set(['INCREASE', 'TRANSFER_IN', 'INVEST']);

export function canEditPendingTransaction(transaction) {
    return transaction?.status === 'PENDING'
        && !(transaction.source === 'TRANSFER_IN' && transaction.relatedTransactionId);
}

export function pendingTransactionBody(source, values) {
    return {
        amount: BUY_SOURCES.has(source) ? values.amount : null,
        shares: BUY_SOURCES.has(source) ? null : values.shares,
        tradeDate: `${dayjs(values.tradeDate).utcOffset(8).format('YYYY-MM-DD')}T00:00:00+08:00`,
    };
}

export function adjustmentFromTarget(currentShares, targetShares) {
    const current = Number(currentShares ?? 0);
    const target = Number(targetShares);
    if (!Number.isFinite(current) || !Number.isFinite(target)) return null;

    const difference = Number((target - current).toFixed(2));
    if (difference === 0) return null;
    return {
        source: difference > 0 ? 'ADJUST_IN' : 'ADJUST_OUT',
        shares: Math.abs(difference),
    };
}

export {BUY_SOURCES};
