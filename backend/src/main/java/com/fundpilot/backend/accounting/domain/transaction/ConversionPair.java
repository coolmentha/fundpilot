package com.fundpilot.backend.accounting.domain.transaction;

/** 基金转换的转出/转入双腿；两腿必须同日原子确认。 */
public record ConversionPair(LedgerTransaction outLeg, LedgerTransaction inLeg) {

    /** 两条互指流水构成转换对时返回配对，否则返回 null。 */
    public static ConversionPair resolve(LedgerTransaction first, LedgerTransaction second) {
        if (first == null || second == null) {
            return null;
        }
        if (first.source() == TransactionSource.TRANSFER_OUT
                && second.source() == TransactionSource.TRANSFER_IN) {
            return new ConversionPair(first, second);
        }
        if (first.source() == TransactionSource.TRANSFER_IN
                && second.source() == TransactionSource.TRANSFER_OUT) {
            return new ConversionPair(second, first);
        }
        return null;
    }
}
