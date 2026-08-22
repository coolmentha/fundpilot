package com.fundpilot.backend.importing.adapter.web.importsession;

import com.fundpilot.backend.importing.application.command.importsession.YangjibaoImportCommandHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "养基宝导入接口", description = "养基宝导入相关操作")
@RestController
@RequestMapping("/api/imports/yangjibao")
@RequiredArgsConstructor
public class YangjibaoImportController {
    private final YangjibaoImportCommandHandler commands;

    @PostMapping("/sessions")
    @Operation(summary = "创建导入会话")
    public ImportingApiResponse<YangjibaoImportCommandHandler.SessionView> create() {
        return ImportingApiResponse.ok(commands.create());
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "查询导入会话状态")
    public ImportingApiResponse<YangjibaoImportCommandHandler.SessionView> state(@PathVariable String id) {
        return ImportingApiResponse.ok(commands.state(id));
    }

    @GetMapping("/sessions/{id}/preview")
    @Operation(summary = "预览导入数据")
    public ImportingApiResponse<List<YangjibaoImportCommandHandler.PreviewItem>> preview(@PathVariable String id) {
        return ImportingApiResponse.ok(commands.preview(id));
    }

    @PostMapping("/sessions/{id}/import")
    @Operation(summary = "执行导入")
    public ImportingApiResponse<YangjibaoImportCommandHandler.ImportJobView> run(@PathVariable String id,
                                                                        @RequestBody ImportRequest request) {
        return ImportingApiResponse.ok(commands.startImport(id, request.items().stream()
                .map(item -> new YangjibaoImportCommandHandler.Selection(item.itemId(),
                        item.existingMode() == null ? null
                                : YangjibaoImportCommandHandler.ExistingMode.valueOf(item.existingMode().name())))
                .toList()));
    }

    @GetMapping("/sessions/{id}/import")
    @Operation(summary = "查询导入任务状态")
    public ImportingApiResponse<YangjibaoImportCommandHandler.ImportJobView> importStatus(@PathVariable String id) {
        return ImportingApiResponse.ok(commands.importStatus(id));
    }

    @PostMapping("/sessions/{id}/import/retry")
    @Operation(summary = "重试失败条目导入")
    public ImportingApiResponse<YangjibaoImportCommandHandler.ImportJobView> retryFailed(@PathVariable String id) {
        return ImportingApiResponse.ok(commands.retryFailed(id));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "取消导入会话")
    public ImportingApiResponse<Void> cancel(@PathVariable String id) {
        commands.cancel(id);
        return ImportingApiResponse.ok(null);
    }

    @Schema(description = "养基宝导入请求")
    public record ImportRequest(
            @Schema(description = "待导入条目选择列表") List<Selection> items) {
    }

    @Schema(description = "导入条目选择")
    public record Selection(
            @Schema(description = "养基宝条目ID", example = "10001") String itemId,
            @Schema(description = "已存在数据处理方式，枚举（KEEP_LOCAL 保留本地 / SYNC_TARGET 同步目标）", example = "KEEP_LOCAL") ExistingMode existingMode) {
    }

    @Schema(description = "已存在数据处理方式枚举")
    public enum ExistingMode {KEEP_LOCAL, SYNC_TARGET}
}
