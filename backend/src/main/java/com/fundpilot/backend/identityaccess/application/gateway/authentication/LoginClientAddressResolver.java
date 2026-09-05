package com.fundpilot.backend.identityaccess.application.gateway.authentication;

public interface LoginClientAddressResolver {

    String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    String resolve(String remoteAddress, String forwardedFor);
}
