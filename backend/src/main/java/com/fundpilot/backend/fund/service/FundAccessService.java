package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.fundpilot.backend.fund.entity.FundEntity;

@Service
@RequiredArgsConstructor
public class FundAccessService {
    private final FundRepository repository;
    private final CurrentUserService currentUserService;

    public void requireOwned(Long fundId) {
        long userId = currentUserService.userId();
        if (userId != 0L && repository.findByIdAndOwnerId(fundId, userId).isEmpty()) {
            throw ErrorCode.FUND_NOT_FOUND.toException("Fund #" + fundId + " 不存在");
        }
    }

    public void requireOwned(FundEntity fund) {
        if (!isOwned(fund)) throw ErrorCode.FUND_NOT_FOUND.toException("Fund 不存在");
    }

    public boolean isOwned(FundEntity fund) {
        long userId = currentUserService.userId();
        return userId == 0L || fund != null && Long.valueOf(userId).equals(fund.getOwnerId());
    }
}
