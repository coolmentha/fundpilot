package com.fundpilot.backend.alerting.domain.condition;

/** 指标的可调参数定义：前端据此渲染输入框，服务端据此校验取值并兜底默认值。 */
public record IndicatorParameter(String name, String label, int defaultValue, int minimum, int maximum) {

    public IndicatorParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("指标参数名称不能为空");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("指标参数说明不能为空");
        }
        if (minimum > maximum) {
            throw new IllegalArgumentException("指标参数取值范围不合法: " + name);
        }
        if (defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("指标参数默认值超出取值范围: " + name);
        }
        name = name.trim();
        label = label.trim();
    }

    /** 校验取值落在声明范围内并返回。 */
    public int requireInRange(int value) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(label + "必须在 " + minimum + "~" + maximum + " 之间");
        }
        return value;
    }
}