/**
 * 本文件实现 SystemService 业务服务。
 */
package com.example.wms.service;

import com.example.wms.api.SystemController.CurrentUserView;
import com.example.wms.api.SystemController.AuthSessionView;
import com.example.wms.api.SystemController.LoginRequest;
import com.example.wms.api.SystemController.MenuNodeView;
import com.example.wms.api.SystemController.MenuSaveRequest;
import com.example.wms.api.SystemController.MenuView;
import com.example.wms.api.SystemController.RegisterRequest;
import com.example.wms.api.SystemController.RoleMenuAssignRequest;
import com.example.wms.api.SystemController.RoleSaveRequest;
import com.example.wms.api.SystemController.RoleView;
import com.example.wms.api.SystemController.UserSaveRequest;
import com.example.wms.api.SystemController.UserView;
import com.example.wms.common.AuthContext;
import com.example.wms.common.BusinessException;
import com.example.wms.common.NotFoundException;
import com.example.wms.domain.AppRole;
import com.example.wms.domain.AppUser;
import com.example.wms.domain.MenuItem;
import com.example.wms.domain.RoleMenu;
import com.example.wms.repo.AppRoleRepository;
import com.example.wms.repo.AppUserRepository;
import com.example.wms.repo.MenuItemRepository;
import com.example.wms.repo.RoleMenuRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SystemService {
    private static final Set<String> PERMISSION_LEVELS = Set.of("ADMIN", "MANAGER", "OPERATOR", "VIEWER");

    private final AppUserRepository appUserRepository;
    private final MenuItemRepository menuItemRepository;
    private final AppRoleRepository appRoleRepository;
    private final RoleMenuRepository roleMenuRepository;

    public SystemService(AppUserRepository appUserRepository,
                         MenuItemRepository menuItemRepository,
                         AppRoleRepository appRoleRepository,
                         RoleMenuRepository roleMenuRepository) {
        this.appUserRepository = appUserRepository;
        this.menuItemRepository = menuItemRepository;
        this.appRoleRepository = appRoleRepository;
        this.roleMenuRepository = roleMenuRepository;
    }

    public CurrentUserView getCurrentUser() {
        return toCurrentUser(AuthContext.getUser());
    }

    public AuthSessionView login(LoginRequest request) {
        AppUser user = appUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("????????"));
        if (!hashPassword(request.password()).equals(user.getPasswordHash())) {
            throw new BusinessException("????????");
        }
        user.setAuthToken(UUID.randomUUID().toString().replace("-", ""));
        user = appUserRepository.save(user);
        return new AuthSessionView(user.getAuthToken(), toCurrentUser(user));
    }

    public AuthSessionView register(RegisterRequest request) {
        appUserRepository.findByUsername(request.username()).ifPresent(existing -> {
            throw new BusinessException("??????");
        });
        String roleName = normalizeRole(request.roleName(), "WAREHOUSE_OPERATOR");
        if ("SUPER_ADMIN".equals(roleName)) {
            throw new BusinessException("????????????");
        }
        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setPasswordHash(hashPassword(request.password()));
        user.setRoleName(roleName);
        user.setAvatarColor(colorForRole(roleName));
        user.setAuthToken(UUID.randomUUID().toString().replace("-", ""));
        user = appUserRepository.save(user);
        return new AuthSessionView(user.getAuthToken(), toCurrentUser(user));
    }

    public void logout() {
        AppUser user = AuthContext.getUser();
        user.setAuthToken(null);
        appUserRepository.save(user);
    }

    private CurrentUserView toCurrentUser(AppUser user) {
        return new CurrentUserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleName(), user.getAvatarColor());
    }

    public List<UserView> listUsers() {
        requireAdmin();
        return appUserRepository.findAllByOrderByUsernameAsc().stream().map(this::toUserView).toList();
    }

    public UserView createUser(UserSaveRequest request) {
        requireAdmin();
        appUserRepository.findByUsername(request.username()).ifPresent(existing -> {
            throw new BusinessException("??????");
        });
        AppUser user = new AppUser();
        user.setUsername(request.username());
        applyUserRequest(user, request, true);
        return toUserView(appUserRepository.save(user));
    }

    public UserView updateUser(Long id, UserSaveRequest request) {
        requireAdmin();
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("?????"));
        appUserRepository.findByUsername(request.username()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BusinessException("??????");
            }
        });
        user.setUsername(request.username());
        applyUserRequest(user, request, false);
        return toUserView(appUserRepository.save(user));
    }

    public void deleteUser(Long id) {
        requireAdmin();
        AppUser currentUser = AuthContext.getUser();
        if (currentUser.getId().equals(id)) {
            throw new BusinessException("??????????");
        }
        if (!appUserRepository.existsById(id)) {
            throw new NotFoundException("?????");
        }
        appUserRepository.deleteById(id);
    }

    public List<RoleView> listRoles() {
        requireAdmin();
        return appRoleRepository.findAllByOrderByRoleCodeAsc().stream().map(this::toRoleView).toList();
    }

    public RoleView createRole(RoleSaveRequest request) {
        requireAdmin();
        String roleCode = normalizeCode(request.roleCode());
        appRoleRepository.findByRoleCode(roleCode).ifPresent(existing -> {
            throw new BusinessException("???????");
        });
        AppRole role = new AppRole();
        role.setRoleCode(roleCode);
        applyRoleRequest(role, request);
        role = appRoleRepository.save(role);
        saveRoleMenus(role.getRoleCode(), request.menuIds());
        return toRoleView(role);
    }

    public RoleView updateRole(Long id, RoleSaveRequest request) {
        requireAdmin();
        AppRole role = appRoleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("?????"));
        String roleCode = normalizeCode(request.roleCode());
        appRoleRepository.findByRoleCode(roleCode).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BusinessException("???????");
            }
        });
        String oldCode = role.getRoleCode();
        role.setRoleCode(roleCode);
        applyRoleRequest(role, request);
        role = appRoleRepository.save(role);
        if (!oldCode.equals(roleCode)) {
            List<RoleMenu> oldMenus = roleMenuRepository.findByRoleCode(oldCode);
            roleMenuRepository.deleteByRoleCode(oldCode);
            oldMenus.forEach(item -> {
                item.setRoleCode(roleCode);
                roleMenuRepository.save(item);
            });
            appUserRepository.findAll().stream()
                    .filter(user -> oldCode.equals(user.getRoleName()))
                    .forEach(user -> {
                        user.setRoleName(roleCode);
                        appUserRepository.save(user);
                    });
        }
        saveRoleMenus(role.getRoleCode(), request.menuIds());
        return toRoleView(role);
    }

    public void deleteRole(Long id) {
        requireAdmin();
        AppRole role = appRoleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("?????"));
        if ("SUPER_ADMIN".equals(role.getRoleCode())) {
            throw new BusinessException("?????????????");
        }
        if (appUserRepository.countByRoleName(role.getRoleCode()) > 0) {
            throw new BusinessException("??????????????");
        }
        roleMenuRepository.deleteByRoleCode(role.getRoleCode());
        appRoleRepository.deleteById(id);
    }

    public RoleView assignRoleMenus(String roleCode, RoleMenuAssignRequest request) {
        requireAdmin();
        AppRole role = appRoleRepository.findByRoleCode(normalizeCode(roleCode))
                .orElseThrow(() -> new NotFoundException("?????"));
        saveRoleMenus(role.getRoleCode(), request.menuIds());
        return toRoleView(role);
    }

    public List<MenuNodeView> getMenuTree() {

        Set<String> allowedMenuKeys = allowedMenuKeys(AuthContext.getUser().getRoleName());
        List<MenuItem> items = menuItemRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(MenuItem::isVisible)
                .filter(item -> allowedMenuKeys.contains(item.getMenuKey()))
                .toList();
        Map<Long, MenuNodeView> nodeMap = new HashMap<>();
        List<MenuNodeView> roots = new ArrayList<>();

        for (MenuItem item : items) {
            nodeMap.put(item.getId(), toNode(item));
        }

        for (MenuItem item : items) {
            MenuNodeView node = nodeMap.get(item.getId());
            if (item.getParentId() == null) {
                roots.add(node);
            } else {
                MenuNodeView parent = nodeMap.get(item.getParentId());
                if (parent != null) {
                    parent.children().add(node);
                }
            }
        }

        roots.forEach(this::sortRecursively);
        roots.sort(Comparator.comparing(MenuNodeView::sortOrder).thenComparing(MenuNodeView::id));
        return roots;
    }

    public List<MenuView> listMenus() {
        if (isAdmin(AuthContext.getUser().getRoleName())) {
            return menuItemRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                    .map(this::toView)
                    .toList();
        }
        Set<String> allowedMenuKeys = allowedMenuKeys(AuthContext.getUser().getRoleName());
        return menuItemRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(item -> allowedMenuKeys.contains(item.getMenuKey()))
                .map(this::toView)
                .toList();
    }

    public MenuView createMenu(MenuSaveRequest request) {
        requireAdmin();
        menuItemRepository.findByMenuKey(request.menuKey()).ifPresent(existing -> {
            throw new BusinessException("???????");
        });
        MenuItem menu = menuItemRepository.save(fromRequest(new MenuItem(), request));
        grantMenuToAdmins(menu.getId());
        return toView(menu);
    }

    public MenuView updateMenu(Long id, MenuSaveRequest request) {
        requireAdmin();
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("?????"));
        menuItemRepository.findByMenuKey(request.menuKey()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BusinessException("???????");
            }
        });
        return toView(menuItemRepository.save(fromRequest(menuItem, request)));
    }

    public void deleteMenu(Long id) {
        requireAdmin();
        boolean hasChildren = menuItemRepository.findAll().stream().anyMatch(item -> id.equals(item.getParentId()));
        if (hasChildren) {
            throw new BusinessException("???????");
        }
        roleMenuRepository.deleteByMenuId(id);
        menuItemRepository.deleteById(id);
    }

    private MenuItem fromRequest(MenuItem item, MenuSaveRequest request) {
        item.setParentId(request.parentId());
        item.setMenuKey(request.menuKey());
        item.setMenuName(request.menuName());
        item.setMenuType(request.menuType());
        item.setPathKey(request.pathKey());
        item.setPageKey(request.pageKey());
        item.setIconKey(request.iconKey());
        item.setSortOrder(request.sortOrder());
        item.setVisible(request.visible());
        return item;
    }

    private MenuNodeView toNode(MenuItem item) {
        return new MenuNodeView(
                item.getId(),
                item.getParentId(),
                item.getMenuKey(),
                item.getMenuName(),
                item.getMenuType(),
                item.getPathKey(),
                item.getPageKey(),
                item.getIconKey(),
                item.getSortOrder(),
                new ArrayList<>()
        );
    }

    private MenuView toView(MenuItem item) {
        return new MenuView(
                item.getId(),
                item.getParentId(),
                item.getMenuKey(),
                item.getMenuName(),
                item.getMenuType(),
                item.getPathKey(),
                item.getPageKey(),
                item.getIconKey(),
                item.getSortOrder(),
                item.isVisible()
        );
    }

    private void sortRecursively(MenuNodeView node) {
        node.children().sort(Comparator.comparing(MenuNodeView::sortOrder).thenComparing(MenuNodeView::id));
        node.children().forEach(this::sortRecursively);
    }

    private Set<String> allowedMenuKeys(String roleName) {
        if (isAdmin(roleName)) {
            return menuItemRepository.findAll().stream().map(MenuItem::getMenuKey).collect(Collectors.toSet());
        }
        Set<Long> menuIds = roleMenuRepository.findByRoleCode(normalizeRole(roleName, "VIEWER")).stream()
                .map(RoleMenu::getMenuId)
                .collect(Collectors.toSet());
        return menuItemRepository.findAll().stream()
                .filter(menu -> menuIds.contains(menu.getId()))
                .map(MenuItem::getMenuKey)
                .collect(Collectors.toSet());
    }

    private void requireAdmin() {
        if (!isAdmin(AuthContext.getUser().getRoleName())) {
            throw new BusinessException("Permission denied");
        }
    }

    private String normalizeRole(String value, String fallback) {
        String role = normalizeCode(value == null || value.isBlank() ? fallback : value);
        AppRole appRole = appRoleRepository.findByRoleCode(role)
                .orElseThrow(() -> new BusinessException("Role is not supported"));
        if (!appRole.isEnabled()) {
            throw new BusinessException("Role is disabled");
        }
        return role;
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Code is required");
        }
        return value.trim().toUpperCase();
    }

    private void applyUserRequest(AppUser user, UserSaveRequest request, boolean creating) {
        user.setDisplayName(request.displayName());
        user.setRoleName(normalizeRole(request.roleName(), "WAREHOUSE_OPERATOR"));
        String password = request.password() == null ? "" : request.password().trim();
        if (creating && password.isEmpty()) {
            throw new BusinessException("Password is required");
        }
        if (!password.isEmpty()) {
            user.setPasswordHash(hashPassword(password));
        }
        String color = request.avatarColor() == null || request.avatarColor().isBlank()
                ? colorForRole(user.getRoleName())
                : request.avatarColor().trim();
        user.setAvatarColor(color);
    }

    private void applyRoleRequest(AppRole role, RoleSaveRequest request) {
        String permissionLevel = normalizeCode(request.permissionLevel());
        if (!PERMISSION_LEVELS.contains(permissionLevel)) {
            throw new BusinessException("Permission level is not supported");
        }
        role.setRoleName(request.roleName());
        role.setPermissionLevel(permissionLevel);
        role.setDescription(request.description());
        role.setEnabled(request.enabled());
        if (role.getCreatedAt() == null) {
            role.setCreatedAt(java.time.LocalDateTime.now());
        }
    }

    private void saveRoleMenus(String roleCode, List<Long> menuIds) {
        Set<Long> validMenuIds = menuItemRepository.findAll().stream().map(MenuItem::getId).collect(Collectors.toSet());
        Set<Long> normalizedIds = menuIds == null ? Set.of() : menuIds.stream()
                .filter(validMenuIds::contains)
                .collect(Collectors.toCollection(HashSet::new));
        roleMenuRepository.deleteAll(roleMenuRepository.findByRoleCode(roleCode));
        roleMenuRepository.flush();
        normalizedIds.forEach(menuId -> {
            RoleMenu roleMenu = new RoleMenu();
            roleMenu.setRoleCode(roleCode);
            roleMenu.setMenuId(menuId);
            roleMenuRepository.save(roleMenu);
        });
    }

    private void grantMenuToAdmins(Long menuId) {
        appRoleRepository.findAll().stream()
                .filter(role -> "ADMIN".equals(role.getPermissionLevel()))
                .forEach(role -> {
                    boolean exists = roleMenuRepository.findByRoleCode(role.getRoleCode()).stream()
                            .anyMatch(item -> menuId.equals(item.getMenuId()));
                    if (!exists) {
                        RoleMenu roleMenu = new RoleMenu();
                        roleMenu.setRoleCode(role.getRoleCode());
                        roleMenu.setMenuId(menuId);
                        roleMenuRepository.save(roleMenu);
                    }
                });
    }

    private UserView toUserView(AppUser user) {
        String roleDisplayName = appRoleRepository.findByRoleCode(user.getRoleName())
                .map(AppRole::getRoleName)
                .orElse(user.getRoleName());
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleName(), roleDisplayName, user.getAvatarColor());
    }

    private RoleView toRoleView(AppRole role) {
        List<Long> menuIds = roleMenuRepository.findByRoleCode(role.getRoleCode()).stream()
                .map(RoleMenu::getMenuId)
                .toList();
        return new RoleView(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getPermissionLevel(),
                role.getDescription(),
                role.isEnabled(),
                menuIds
        );
    }

    private boolean isAdmin(String roleName) {
        return appRoleRepository.findByRoleCode(roleName)
                .map(role -> "ADMIN".equals(role.getPermissionLevel()))
                .orElse("SUPER_ADMIN".equals(roleName));
    }

    private String rolePermissionLevel(String roleName) {
        return appRoleRepository.findByRoleCode(roleName)
                .map(AppRole::getPermissionLevel)
                .orElseGet(() -> switch (roleName) {
                    case "SUPER_ADMIN" -> "ADMIN";
                    case "WAREHOUSE_MANAGER" -> "MANAGER";
                    case "WAREHOUSE_OPERATOR" -> "OPERATOR";
                    default -> "VIEWER";
                });
    }

    private String colorForRole(String roleName) {
        return switch (rolePermissionLevel(roleName)) {
            case "ADMIN" -> "#0f766e";
            case "MANAGER" -> "#2563eb";
            case "OPERATOR" -> "#7c3aed";
            default -> "#64748b";
        };
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException("Password hashing failed");
        }
    }
}
