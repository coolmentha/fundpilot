package com.fundpilot.backend.identityaccess.adapter.api.userdirectory;

import com.fundpilot.backend.identityaccess.application.query.userdirectory.UserDirectoryQueryHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserDirectoryApi {

    private final UserDirectoryQueryHandler queries;

    public List<Long> activeUserIds() {
        return queries.activeUserIds();
    }
}
