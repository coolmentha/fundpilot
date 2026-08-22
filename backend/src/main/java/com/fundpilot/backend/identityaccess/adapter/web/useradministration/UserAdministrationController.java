package com.fundpilot.backend.identityaccess.adapter.web.useradministration;

import com.fundpilot.backend.identityaccess.adapter.web.authentication.IdentityApiResponse;
import com.fundpilot.backend.identityaccess.application.command.useradministration.UserAdministrationCommandHandler;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActorQueryHandler;
import com.fundpilot.backend.identityaccess.application.query.useradministration.UserAdministrationQueryHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理 - 用户管理接口", description = "用户管理相关操作")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdministrationController {

    private final CurrentActorQueryHandler actors;
    private final UserAdministrationCommandHandler commands;
    private final UserAdministrationQueryHandler queries;

    @GetMapping
    @Operation(summary = "查询用户列表")
    public IdentityApiResponse<List<UserResult>> list() {
        return IdentityApiResponse.ok(queries.list(actors.current()).stream()
                .map(user -> new UserResult(user.id(), user.username(), user.role().name(), user.enabled()))
                .toList());
    }

    @PostMapping
    @Operation(summary = "创建用户")
    public IdentityApiResponse<UserResult> create(@RequestBody UserRequest request) {
        return IdentityApiResponse.ok(toResult(commands.create(
                actors.current(), request.username(), request.password(), request.role())));
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "更新用户启用状态")
    public IdentityApiResponse<UserResult> updateStatus(@PathVariable long id,
                                                        @RequestBody StatusRequest request) {
        return IdentityApiResponse.ok(toResult(commands.updateStatus(actors.current(), id, request.enabled())));
    }

    @PostMapping("/{id}/role")
    @Operation(summary = "更新用户角色")
    public IdentityApiResponse<UserResult> updateRole(@PathVariable long id,
                                                      @RequestBody RoleRequest request) {
        return IdentityApiResponse.ok(toResult(commands.updateRole(actors.current(), id, request.role())));
    }

    private UserResult toResult(UserAdministrationCommandHandler.UserResult user) {
        return new UserResult(user.id(), user.username(), user.role().name(), user.enabled());
    }

    @Schema(description = "创建用户请求")
    public record UserRequest(
            @Schema(description = "用户名", example = "zhangsan") String username,
            @Schema(description = "密码", example = "P@ssw0rd123") String password,
            @Schema(description = "用户角色，枚举（USER 普通用户 / ADMIN 管理员）", example = "USER") UserAdministrationCommandHandler.Role role) {
    }

    @Schema(description = "更新用户状态请求")
    public record StatusRequest(
            @Schema(description = "是否启用，true 启用 / false 禁用", example = "true") boolean enabled) {
    }

    @Schema(description = "更新用户角色请求")
    public record RoleRequest(
            @Schema(description = "用户角色，枚举（USER 普通用户 / ADMIN 管理员）", example = "ADMIN") UserAdministrationCommandHandler.Role role) {
    }

    @Schema(description = "用户视图")
    public record UserResult(
            @Schema(description = "用户ID", example = "1") long id,
            @Schema(description = "用户名", example = "zhangsan") String username,
            @Schema(description = "用户角色", example = "USER") String role,
            @Schema(description = "是否启用，true 启用 / false 禁用", example = "true") boolean enabled) {
    }
}
