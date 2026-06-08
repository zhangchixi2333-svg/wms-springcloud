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

/**
 * 系统管理控制器
 * 处理系统认证、菜单管理等基础系统功能
 */
@RestController
@RequestMapping("/api")
public class SystemController {

    /**
     * 系统服务层
     */
    private final SystemService systemService;

    /**
     * 构造函数，注入SystemService
     * @param systemService 系统服务实例
     */
    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    /**
     * 获取当前登录用户信息
     * @return 当前用户视图ApiResponse
     */
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

    /**
     * 用户登出接口
     * @return 操作成功的空响应ApiResponse
     */
    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout() {
        systemService.logout();
        return ApiResponse.okMessage("Logout completed");
    }

    /**
     * 获取树形结构菜单
     * 用于前端左侧菜单渲染，包含父子层级关系
     * @return 菜单节点列表ApiResponse
     */
    @GetMapping("/menus/tree")
    public ApiResponse<List<MenuNodeView>> menuTree() {
        return ApiResponse.ok(systemService.getMenuTree());
    }

    /**
     * 获取所有菜单列表（扁平结构）
     * 用于菜单管理页面展示所有菜单
     * @return 菜单视图列表ApiResponse
     */
    @GetMapping("/menus")
    public ApiResponse<List<MenuView>> listMenus() {
        return ApiResponse.ok(systemService.listMenus());
    }

    /**
     * 创建新菜单
     * @param request 菜单保存请求
     * @return 创建的菜单视图ApiResponse
     */
    @PostMapping("/menus")
    public ApiResponse<MenuView> createMenu(@Valid @RequestBody MenuSaveRequest request) {
        return ApiResponse.ok(systemService.createMenu(request));
    }

    /**
     * 更新现有菜单
     * @param id 菜单ID
     * @param request 菜单保存请求
     * @return 更新后的菜单视图ApiResponse
     */
    @PutMapping("/menus/{id}")
    public ApiResponse<MenuView> updateMenu(@PathVariable Long id, @Valid @RequestBody MenuSaveRequest request) {
        return ApiResponse.ok(systemService.updateMenu(id, request));
    }

    /**
     * 删除菜单
     * @param id 菜单ID
     * @return 操作成功的空响应ApiResponse
     */
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

    /**
     * 当前用户视图记录
     * @param id 用户ID
     * @param username 用户名
     * @param displayName 显示名称
     * @param roleName 角色名称
     * @param avatarColor 头像颜色
     */
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

    /**
     * 树形菜单节点视图记录
     * @param id 菜单ID
     * @param parentId 父菜单ID
     * @param menuKey 菜单标识
     * @param menuName 菜单名称
     * @param menuType 菜单类型
     * @param pathKey 路由路径
     * @param pageKey 页面标识
     * @param iconKey 图标标识
     * @param sortOrder 排序权重
     * @param children 子节点列表
     */
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

    /**
     * 菜单列表视图记录
     * @param id 菜单ID
     * @param parentId 父菜单ID
     * @param menuKey 菜单标识
     * @param menuName 菜单名称
     * @param menuType 菜单类型
     * @param pathKey 路由路径
     * @param pageKey 页面标识
     * @param iconKey 图标标识
     * @param sortOrder 排序权重
     * @param visible 是否可见
     */
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

    /**
     * 菜单保存请求记录
     * @param parentId 父菜单ID
     * @param menuKey 菜单标识
     * @param menuName 菜单名称
     * @param menuType 菜单类型
     * @param pathKey 路由路径
     * @param pageKey 页面标识
     * @param iconKey 图标标识
     * @param sortOrder 排序权重，0-999之间
     * @param visible 是否可见
     */
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
