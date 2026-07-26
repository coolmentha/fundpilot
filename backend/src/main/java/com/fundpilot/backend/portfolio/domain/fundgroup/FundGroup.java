package com.fundpilot.backend.portfolio.domain.fundgroup;

import java.util.Locale;

public record FundGroup(Long id, long ownerId, String name, int sortOrder) {
    private static final int MAX_NAME_LENGTH = 20;

    public FundGroup {
        name = normalizeName(name);
        if (sortOrder < 0) {
            throw new IllegalArgumentException("分组排序不能为负数");
        }
    }

    public static String normalizeName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || value.length() > MAX_NAME_LENGTH
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("分组名称长度必须为 1-20 个字符且不能包含控制字符");
        }
        return value;
    }

    public String normalizedKey() {
        return name.toLowerCase(Locale.ROOT);
    }
}
