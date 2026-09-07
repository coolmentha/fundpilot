package com.fundpilot.backend.importing.adapter.web.importsession;

import com.fundpilot.backend.importing.application.command.importsession.YangjibaoImportCommandHandler.ImportSessionSummary;

import java.time.Instant;

public record YangjibaoImportSessionView(
        String sessionId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        int total,
        int processed,
        int succeeded,
        int failed) {

    public static YangjibaoImportSessionView from(ImportSessionSummary summary) {
        return new YangjibaoImportSessionView(summary.sessionId(), summary.status(), summary.createdAt(),
                summary.updatedAt(), summary.expiresAt(), summary.total(), summary.processed(), summary.succeeded(),
                summary.failed());
    }
}
