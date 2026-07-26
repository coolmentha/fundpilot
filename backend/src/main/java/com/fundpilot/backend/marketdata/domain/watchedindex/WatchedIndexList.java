package com.fundpilot.backend.marketdata.domain.watchedindex;

import java.util.List;

public record WatchedIndexList(long ownerId, List<String> indexCodes) {
    public WatchedIndexList {
        if (ownerId <= 0) throw new IllegalArgumentException("用户标识必须为正数");
        indexCodes = indexCodes == null ? List.of() : indexCodes.stream()
                .map(String::trim).filter(code -> !code.isEmpty()).distinct().toList();
        if (indexCodes.size() > 20) throw new IllegalArgumentException("关注指数最多 20 个");
        if (indexCodes.stream().anyMatch(code -> code.length() > 64)) {
            throw new IllegalArgumentException("指数代码过长");
        }
    }
}
