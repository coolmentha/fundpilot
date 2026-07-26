package com.fundpilot.backend.marketdata.application.gateway.watchedindex;

import com.fundpilot.backend.marketdata.application.event.watchedindex.WatchedIndicesChanged;

/** 关注指数能力对外发布集成事件的出站契约；投递实现放在 {@code infrastructure.messaging}。 */
public interface WatchedIndexEventGateway {

    void publishWatchedIndicesChanged(WatchedIndicesChanged event);
}
