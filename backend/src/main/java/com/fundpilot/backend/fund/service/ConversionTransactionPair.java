package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;

record ConversionTransactionPair(FundTransactionEntity outLeg, FundTransactionEntity inLeg) {

    static ConversionTransactionPair resolve(FundTransactionEntity first, FundTransactionEntity second) {
        if (first == null || second == null) {
            return null;
        }
        if (first.getSource() == FundTransactionSource.TRANSFER_OUT
                && second.getSource() == FundTransactionSource.TRANSFER_IN) {
            return new ConversionTransactionPair(first, second);
        }
        if (first.getSource() == FundTransactionSource.TRANSFER_IN
                && second.getSource() == FundTransactionSource.TRANSFER_OUT) {
            return new ConversionTransactionPair(second, first);
        }
        return null;
    }
}
