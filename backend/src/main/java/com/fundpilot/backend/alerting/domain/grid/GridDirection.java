package com.fundpilot.backend.alerting.domain.grid;

/** 网格命中后的操作方向：高于上线路建议卖出，低于下线路建议买入。 */
public enum GridDirection {

    /** 盘中估值不低于上线路：建议卖出一格。 */
    SELL("建议卖出"),
    /** 盘中估值不高于下线路：建议买入一格。 */
    BUY("建议买入");

    private final String label;

    GridDirection(String label) {
        this.label = label;
    }

    /** 邮件正文用的中文建议文案。 */
    public String label() {
        return label;
    }
}
