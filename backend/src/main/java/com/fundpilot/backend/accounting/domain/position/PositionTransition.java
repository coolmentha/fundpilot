package com.fundpilot.backend.accounting.domain.position;

/** 持仓状态迁移事实；由应用层转换为 {@code PositionOpened}/{@code PositionCleared} 集成事件。 */
public record PositionTransition(long portfolioFundId, long ownerId,
                                 PositionStatus previous, PositionStatus current) {

    public boolean opened() {
        return current == PositionStatus.OPEN;
    }

    public boolean cleared() {
        return current == PositionStatus.CLEARED;
    }
}
