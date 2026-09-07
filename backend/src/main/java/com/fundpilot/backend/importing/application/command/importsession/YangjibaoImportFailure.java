package com.fundpilot.backend.importing.application.command.importsession;

public final class YangjibaoImportFailure extends RuntimeException {
    private final Code code;

    public YangjibaoImportFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    public YangjibaoImportFailure(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() { return code; }

    public enum Code {
        YANGJIBAO_SESSION_NOT_FOUND,
        YANGJIBAO_SESSION_INVALID,
        YANGJIBAO_API_FAILED,
        YANGJIBAO_IMPORT_INVALID,
        YANGJIBAO_IMPORT_VALIDATION_FAILED,
        YANGJIBAO_IMPORT_CONFLICT,
        YANGJIBAO_IMPORT_DEPENDENCY_FAILED
    }
}
