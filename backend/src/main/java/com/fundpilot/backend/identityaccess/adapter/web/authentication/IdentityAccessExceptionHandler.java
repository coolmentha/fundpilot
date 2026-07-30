package com.fundpilot.backend.identityaccess.adapter.web.authentication;

import com.fundpilot.backend.identityaccess.application.command.authentication.AuthenticationFailure;
import com.fundpilot.backend.identityaccess.application.command.useradministration.UserAdministrationFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.fundpilot.backend.identityaccess.adapter.web")
class IdentityAccessExceptionHandler {

    @ExceptionHandler(AuthenticationFailure.class)
    ResponseEntity<IdentityApiResponse<Void>> authentication(AuthenticationFailure failure) {
        int status = switch (failure.code()) {
            case ADMIN_UNAUTHORIZED -> 401;
            case ADMIN_FORBIDDEN -> 403;
            case ADMIN_AUTH_NOT_CONFIGURED -> 503;
        };
        return ResponseEntity.status(status)
                .body(IdentityApiResponse.error(failure.code().name(), failure.getMessage()));
    }

    @ExceptionHandler(UserAdministrationFailure.class)
    ResponseEntity<IdentityApiResponse<Void>> administration(UserAdministrationFailure failure) {
        int status = failure.code() == UserAdministrationFailure.Code.ADMIN_FORBIDDEN ? 403 : 400;
        return ResponseEntity.status(status)
                .body(IdentityApiResponse.error(failure.code().name(), failure.getMessage()));
    }
}
