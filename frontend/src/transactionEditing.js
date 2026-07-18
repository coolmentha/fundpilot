const BUY_SOURCES = new Set(['INCREASE', 'TRANSFER_IN', 'INVEST']);

export function canEditPendingTransaction(transaction) {
    return transaction?.status === 'PENDING'
        && !(transaction.source === 'TRANSFER_IN' && transaction.relatedTransactionId);
}

export function pendingTransactionBody(source, values) {
    return {
        amount: BUY_SOURCES.has(source) ? values.amount : null,
        shares: BUY_SOURCES.has(source) ? null : values.shares,
        tradeDate: `${values.tradeDate.format('YYYY-MM-DD')}T00:00:00+08:00`,
    };
}

export {BUY_SOURCES};
