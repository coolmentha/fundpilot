package com.fundpilot.backend.market.service.support;

import java.util.Optional;

/**
 * 指数代码格式转换(issue #8):{@code FundSubTypeClassifier} 输出 {@code 000300.SH} 人类可读格式,
 * 东方财富 push2his 接口需要 secid 格式 {@code 1.000300}。本类做两者互转。
 * <ul>
 *   <li>{@code .SH} 后缀 → 前缀 {@code 1.}(沪市)</li>
 *   <li>{@code .SZ} 后缀 → 前缀 {@code 0.}(深市)</li>
 *   <li>{@code .CSI} 后缀 → 前缀 {@code 2.}(中证指数,如 930713.CSI → 2.930713)</li>
 * </ul>
 * 已是 secid 格式或无法识别时返回 {@link Optional#empty()}。
 */
public final class SecidFormat {

    private SecidFormat() {
    }

    /**
     * @param indexCode 指数代码,如 {@code "000300.SH"} / {@code "399006.SZ"} / {@code "930713.CSI"}
     * @return secid 格式,如 {@code "1.000300"} / {@code "0.399006"} / {@code "2.930713"};无法识别返 empty
     */
    public static Optional<String> fromIndexCode(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) {
            return Optional.empty();
        }
        int dot = indexCode.indexOf('.');
        if (dot <= 0 || dot >= indexCode.length() - 1) {
            return Optional.empty();
        }
        String code = indexCode.substring(0, dot);
        String suffix = indexCode.substring(dot + 1).toUpperCase();
        String prefix = switch (suffix) {
            case "SH" -> "1.";
            case "SZ" -> "0.";
            case "CSI" -> "2.";
            default -> null;
        };
        if (prefix == null) {
            return Optional.empty();
        }
        return Optional.of(prefix + code);
    }
}
