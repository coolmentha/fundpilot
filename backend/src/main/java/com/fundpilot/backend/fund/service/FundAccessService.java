package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.fundpilot.backend.fund.entity.FundEntity;

@Service
@RequiredArgsConstructor
public class FundAccessService {
    private final FundRepository repository;
    private final CurrentActorApi currentActorApi;

    public void requireOwned(Long fundId) {
        long userId = currentActorApi.userId();
        if (repository.findByIdAndOwnerId(fundId, userId).isEmpty()) {
            throw ErrorCode.FUND_NOT_FOUND.toException("Fund #" + fundId + " 不存在");
        }
    }

    public void requireOwned(FundEntity fund) {
        if (!isOwned(fund)) throw ErrorCode.FUND_NOT_FOUND.toException("Fund 不存在");
    }

    public boolean isOwned(FundEntity fund) {
        long userId = currentActorApi.userId();
        return fund != null && Long.valueOf(userId).equals(fund.getOwnerId());
    }
}
