package com.fundpilot.backend.identityaccess.adapter.web.useradministration;

import com.fundpilot.backend.identityaccess.adapter.web.authentication.IdentityApiResponse;
import com.fundpilot.backend.identityaccess.application.command.useradministration.UserAdministrationCommandHandler;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActorQueryHandler;
import com.fundpilot.backend.identityaccess.application.query.useradministration.UserAdministrationQueryHandler;
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
public class UserAdministrationController {

    private final CurrentActorQueryHandler actors;
    private final UserAdministrationCommandHandler commands;
    private final UserAdministrationQueryHandler queries;

    @GetMapping
    public IdentityApiResponse<List<UserResult>> list() {
        return IdentityApiResponse.ok(queries.list(actors.current()).stream()
                .map(user -> new UserResult(user.id(), user.username(), user.role().name(), user.enabled()))
                .toList());
    }

    @PostMapping
    public IdentityApiResponse<UserResult> create(@RequestBody UserRequest request) {
        return IdentityApiResponse.ok(toResult(commands.create(
                actors.current(), request.username(), request.password(), request.role())));
    }

    @PostMapping("/{id}/status")
    public IdentityApiResponse<UserResult> updateStatus(@PathVariable long id,
                                                        @RequestBody StatusRequest request) {
        return IdentityApiResponse.ok(toResult(commands.updateStatus(actors.current(), id, request.enabled())));
    }

    @PostMapping("/{id}/role")
    public IdentityApiResponse<UserResult> updateRole(@PathVariable long id,
                                                      @RequestBody RoleRequest request) {
        return IdentityApiResponse.ok(toResult(commands.updateRole(actors.current(), id, request.role())));
    }

    private UserResult toResult(UserAdministrationCommandHandler.UserResult user) {
        return new UserResult(user.id(), user.username(), user.role().name(), user.enabled());
    }

    public record UserRequest(String username, String password, UserAdministrationCommandHandler.Role role) {
    }

    public record StatusRequest(boolean enabled) {
    }

    public record RoleRequest(UserAdministrationCommandHandler.Role role) {
    }

    public record UserResult(long id, String username, String role, boolean enabled) {
    }
}
