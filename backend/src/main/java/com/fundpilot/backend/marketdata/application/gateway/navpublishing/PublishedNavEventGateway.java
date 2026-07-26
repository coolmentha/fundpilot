package com.fundpilot.backend.marketdata.application.gateway.navpublishing;

import com.fundpilot.backend.marketdata.application.event.publishednav.NavPublished;

/** 净值公布能力对外发布集成事件的出站契约；投递实现放在 {@code infrastructure.messaging}。 */
public interface PublishedNavEventGateway {

    void publishNavPublished(NavPublished event);
}
