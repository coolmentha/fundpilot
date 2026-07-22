package com.fundpilot.backend.user.service;

import com.fundpilot.backend.admin.security.AdminApiKeyFilter;
import com.fundpilot.backend.admin.security.AdminSessionTokenService;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class CurrentUserService {
    private final ThreadLocal<Long> backgroundUserId = new ThreadLocal<>();

    public long userId() {
        Long capturedUserId = backgroundUserId.get();
        if (capturedUserId != null) return capturedUserId;
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return 0L;
        var request = attributes.getRequest();
        Object value = request.getAttribute(AdminApiKeyFilter.USER_ATTRIBUTE);
        if (value instanceof AdminSessionTokenService.SessionIdentity identity) return identity.userId();
        return 0L;
    }

    public void runAs(long userId, Runnable action) {
        Long previous = backgroundUserId.get();
        backgroundUserId.set(userId);
        try {
            action.run();
        } finally {
            if (previous == null) backgroundUserId.remove();
            else backgroundUserId.set(previous);
        }
    }
}
