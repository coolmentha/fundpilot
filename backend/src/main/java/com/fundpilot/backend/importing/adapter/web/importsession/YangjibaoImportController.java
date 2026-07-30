package com.fundpilot.backend.importing.adapter.web.importsession;

import com.fundpilot.backend.importing.application.command.importsession.YangjibaoImportCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/imports/yangjibao")
@RequiredArgsConstructor
public class YangjibaoImportController {
    private final YangjibaoImportCommandHandler commands;

    @PostMapping("/sessions")
    public ImportingApiResponse<YangjibaoImportCommandHandler.SessionView> create() {
        return ImportingApiResponse.ok(commands.create());
    }

    @GetMapping("/sessions/{id}")
    public ImportingApiResponse<YangjibaoImportCommandHandler.SessionView> state(@PathVariable String id) {
        return ImportingApiResponse.ok(commands.state(id));
    }

    @GetMapping("/sessions/{id}/preview")
    public ImportingApiResponse<List<YangjibaoImportCommandHandler.PreviewItem>> preview(@PathVariable String id) {
        return ImportingApiResponse.ok(commands.preview(id));
    }

    @PostMapping("/sessions/{id}/import")
    public ImportingApiResponse<YangjibaoImportCommandHandler.ImportJobView> run(@PathVariable String id,
                                                                        @RequestBody ImportRequest request) {
        return ImportingApiResponse.ok(commands.startImport(id, request.items().stream()
                .map(item -> new YangjibaoImportCommandHandler.Selection(item.itemId(),
                        item.existingMode() == null ? null
                                : YangjibaoImportCommandHandler.ExistingMode.valueOf(item.existingMode().name())))
                .toList()));
    }

    @GetMapping("/sessions/{id}/import")
    public ImportingApiResponse<YangjibaoImportCommandHandler.ImportJobView> importStatus(@PathVariable String id) {
        return ImportingApiResponse.ok(commands.importStatus(id));
    }

    @PostMapping("/sessions/{id}/import/retry")
    public ImportingApiResponse<YangjibaoImportCommandHandler.ImportJobView> retryFailed(@PathVariable String id) {
        return ImportingApiResponse.ok(commands.retryFailed(id));
    }

    @DeleteMapping("/sessions/{id}")
    public ImportingApiResponse<Void> cancel(@PathVariable String id) {
        commands.cancel(id);
        return ImportingApiResponse.ok(null);
    }

    public record ImportRequest(List<Selection> items) {
    }

    public record Selection(String itemId, ExistingMode existingMode) {
    }

    public enum ExistingMode {KEEP_LOCAL, SYNC_TARGET}
}
