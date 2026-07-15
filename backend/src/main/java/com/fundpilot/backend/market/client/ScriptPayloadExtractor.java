package com.fundpilot.backend.market.client;

/**
 * 从受信任变量名或 JSONP 包裹中提取 JSON 值，不执行远端 JavaScript。
 */
final class ScriptPayloadExtractor {

    private ScriptPayloadExtractor() {
    }

    static String assignedValue(String raw, String variableName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int name = raw.indexOf(variableName);
        if (name < 0) {
            return null;
        }
        int equals = raw.indexOf('=', name + variableName.length());
        return equals < 0 ? null : valueStartingAt(raw, equals + 1);
    }

    static String assignedValueByPrefix(String raw, String variablePrefix) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int name = raw.indexOf(variablePrefix);
        if (name < 0) {
            return null;
        }
        int equals = raw.indexOf('=', name + variablePrefix.length());
        return equals < 0 ? null : valueStartingAt(raw, equals + 1);
    }

    static String wrappedValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int parenthesis = raw.indexOf('(');
        return parenthesis < 0 ? valueStartingAt(raw, 0) : valueStartingAt(raw, parenthesis + 1);
    }

    private static String valueStartingAt(String raw, int fromIndex) {
        int start = fromIndex;
        while (start < raw.length() && Character.isWhitespace(raw.charAt(start))) {
            start++;
        }
        if (start >= raw.length()) {
            return null;
        }
        char opening = raw.charAt(start);
        char closing = switch (opening) {
            case '[' -> ']';
            case '{' -> '}';
            default -> 0;
        };
        if (closing == 0) {
            return null;
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == opening) {
                depth++;
            } else if (current == closing && --depth == 0) {
                return raw.substring(start, i + 1);
            }
        }
        return null;
    }
}
