package com.fundpilot.backend.alerting.application.gateway.notificationdelivery;

import java.util.Optional;

/** 提醒收件人来源：用户账号绑定的提醒邮箱。 */
public interface AlertRecipientGateway {

    Optional<String> emailOf(long ownerId);
}
