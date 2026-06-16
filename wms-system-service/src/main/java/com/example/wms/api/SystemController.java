/**
 * 本文件实现 SystemController 控制器。
 */
package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.service.SystemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/auth/me")
    public ApiResponse<CurrentUserView> currentUser() {
        return ApiResponse.ok(systemService.getCurrentUser());
    }

    @PostMapping("/auth/login")
    public ApiResponse<AuthSessionView> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(systemService.login(request));
    }

    @PostMapping("/auth/register")
    public ApiResponse<AuthSessionView> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(systemService.register(request));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout() {
        systemService.logout();
        return ApiResponse.okMessage("Logout completed");
    }

    @GetMapping("/menus/tree")
    public ApiResponse<List<MenuNodeView>> menuTree() {
        return ApiResponse.ok(systemService.getMenuTree());
    }

    @GetMapping("/menus")
    public ApiResponse<List<MenuView>> listMenus() {
        return ApiResponse.ok(systemService.listMenus());
    }

    @PostMapping("/menus")
    public ApiResponse<MenuView> createMenu(@Valid @RequestBody MenuSaveRequest request) {
        return ApiResponse.ok(systemService.createMenu(request));
    }

    @PutMapping("/menus/{id}")
    public ApiResponse<MenuView> updateMenu(@PathVariable Long id, @Valid @RequestBody MenuSaveRequest request) {
        return ApiResponse.ok(systemService.updateMenu(id, request));
    }

    @DeleteMapping("/menus/{id}")
    public ApiResponse<Void> deleteMenu(@PathVariable Long id) {
        systemService.deleteMenu(id);
        return ApiResponse.okMessage("Menu deleted");
    }

    @GetMapping("/users")
    public ApiResponse<List<UserView>> listUsers() {
        return ApiResponse.ok(systemService.listUsers());
    }

    @PostMapping("/users")
    public ApiResponse<UserView> createUser(@Valid @RequestBody UserSaveRequest request) {
        return ApiResponse.ok(systemService.createUser(request));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<UserView> updateUser(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        return ApiResponse.ok(systemService.updateUser(id, request));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        systemService.deleteUser(id);
        return ApiResponse.okMessage("User deleted");
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleView>> listRoles() {
        return ApiResponse.ok(systemService.listRoles());
    }

    @PostMapping("/roles")
    public ApiResponse<RoleView> createRole(@Valid @RequestBody RoleSaveRequest request) {
        return ApiResponse.ok(systemService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<RoleView> updateRole(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest request) {
        return ApiResponse.ok(systemService.updateRole(id, request));
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        systemService.deleteRole(id);
        return ApiResponse.okMessage("Role deleted");
    }

    @PutMapping("/roles/{roleCode}/menus")
    public ApiResponse<RoleView> assignRoleMenus(@PathVariable String roleCode, @RequestBody RoleMenuAssignRequest request) {
        return ApiResponse.ok(systemService.assignRoleMenus(roleCode, request));
    }

    public record CurrentUserView(Long id, String username, String displayName, String roleName, String avatarColor) {
    }

    public record AuthSessionView(String token, CurrentUserView user) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterRequest(@NotBlank String username,
                                  @NotBlank String password,
                                  @NotBlank String displayName,
                                  String roleName) {
    }

    public record UserView(Long id,
                           String username,
                           String displayName,
                           String roleName,
                           String roleDisplayName,
                           String avatarColor) {
    }

    public record UserSaveRequest(@NotBlank String username,
                                  String password,
                                  @NotBlank String displayName,
                                  @NotBlank String roleName,
                                  String avatarColor) {
    }

    public record RoleView(Long id,
                           String roleCode,
                           String roleName,
                           String permissionLevel,
                           String description,
                           boolean enabled,
                           List<Long> menuIds) {
    }

    public record RoleSaveRequest(@NotBlank String roleCode,
                                  @NotBlank String roleName,
                                  @NotBlank String permissionLevel,
                                  String description,
                                  boolean enabled,
                                  List<Long> menuIds) {
    }

    public record RoleMenuAssignRequest(List<Long> menuIds) {
    }

    public record MenuNodeView(Long id,
                               Long parentId,
                               String menuKey,
                               String menuName,
                               String menuType,
                               String pathKey,
                               String pageKey,
                               String iconKey,
                               Integer sortOrder,
                               List<MenuNodeView> children) {
    }

    public record MenuView(Long id,
                           Long parentId,
                           String menuKey,
                           String menuName,
                           String menuType,
                           String pathKey,
                           String pageKey,
                           String iconKey,
                           Integer sortOrder,
                           boolean visible) {
    }

    public record MenuSaveRequest(Long parentId,
                                  @NotBlank String menuKey,
                                  @NotBlank String menuName,
                                  @NotBlank String menuType,
                                  String pathKey,
                                  String pageKey,
                                  String iconKey,
                                  @NotNull @Min(0) @Max(999) Integer sortOrder,
                                  boolean visible) {
    }
}
