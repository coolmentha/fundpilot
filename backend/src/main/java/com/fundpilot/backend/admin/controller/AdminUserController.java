package com.fundpilot.backend.admin.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.user.entity.SiteUserEntity;
import com.fundpilot.backend.user.entity.UserRole;
import com.fundpilot.backend.user.service.AdminUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService service;

    @GetMapping
    public ApiResponse<List<UserView>> list() {
        return ApiResponse.ok(service.list().stream().map(UserView::from).toList());
    }

    @PostMapping
    public ApiResponse<UserView> create(@RequestBody UserRequest request) {
        return ApiResponse.ok(UserView.from(service.create(request.username(), request.password(), request.role())));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<UserView> updateStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return ApiResponse.ok(UserView.from(service.updateStatus(id, request.enabled())));
    }

    @PostMapping("/{id}/role")
    public ApiResponse<UserView> updateRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        return ApiResponse.ok(UserView.from(service.updateRole(id, request.role())));
    }

    public record UserRequest(String username, String password, UserRole role) {}
    public record StatusRequest(boolean enabled) {}
    public record RoleRequest(UserRole role) {}
    public record UserView(Long id, String username, UserRole role, boolean enabled) {
        static UserView from(SiteUserEntity user) {
            return new UserView(user.getId(), user.getUsername(), user.getRole(), user.isEnabled());
        }
    }
}
