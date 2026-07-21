package com.fundpilot.backend.integration.yangjibao;

import com.fundpilot.backend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/imports/yangjibao")
@RequiredArgsConstructor
public class YangjibaoImportController {
    private final YangjibaoImportService service;

    @PostMapping("/sessions") public ApiResponse<YangjibaoImportService.SessionView> create() { return ApiResponse.ok(service.create()); }
    @GetMapping("/sessions/{id}") public ApiResponse<YangjibaoImportService.SessionView> state(@PathVariable String id) { return ApiResponse.ok(service.state(id)); }
    @GetMapping("/sessions/{id}/preview") public ApiResponse<List<YangjibaoImportService.PreviewItem>> preview(@PathVariable String id) { return ApiResponse.ok(service.preview(id)); }
    @PostMapping("/sessions/{id}/import") public ApiResponse<List<YangjibaoImportService.ImportResult>> run(@PathVariable String id, @RequestBody ImportRequest request) { return ApiResponse.ok(service.run(id, request.items())); }
    @DeleteMapping("/sessions/{id}") public ApiResponse<Void> cancel(@PathVariable String id) { service.cancel(id); return ApiResponse.ok(null); }

    public record ImportRequest(List<Selection> items) {}
    public record Selection(String itemId, ExistingMode existingMode) {}
    public enum ExistingMode { KEEP_LOCAL, SYNC_TARGET }
}
