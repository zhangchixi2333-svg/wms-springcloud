package com.example.wms.common;

import com.example.wms.repo.AppUserRepository;
import com.example.wms.repo.AppRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;

    public AuthInterceptor(AppUserRepository appUserRepository, AppRoleRepository appRoleRepository) {
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicPath(request.getRequestURI())) {
            return true;
        }
        String token = extractToken(request.getHeader("Authorization"));
        if (token == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        return appUserRepository.findByAuthToken(token)
                .map(user -> {
                    AuthContext.setUser(user);
                    if (hasAccess(user.getRoleName(), request.getMethod(), request.getRequestURI())) {
                        return true;
                    }
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    return false;
                })
                .orElseGet(() -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    return false;
                });
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean isPublicPath(String path) {
        return path.endsWith("/api/auth/login") || path.endsWith("/api/auth/register");
    }

    private String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix)) {
            return null;
        }
        String token = authorization.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean hasAccess(String roleName, String method, String path) {
        String permissionLevel = permissionLevel(roleName);
        if ("ADMIN".equals(permissionLevel)) {
            return true;
        }
        if (path.endsWith("/api/auth/me") || path.endsWith("/api/auth/logout")) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method)) {
            return true;
        }
        if ("VIEWER".equals(permissionLevel)) {
            return false;
        }
        if ("MANAGER".equals(permissionLevel)) {
            return !path.contains("/api/menus");
        }
        if ("OPERATOR".equals(permissionLevel)) {
            return path.contains("/api/mobile/scan/")
                    || path.contains("/api/inbound-orders")
                    || path.contains("/api/outbound-orders")
                    || path.contains("/api/inventory/kanbans")
                    || path.contains("/api/inventory/manual-entries");
        }
        return false;
    }

    private String permissionLevel(String roleName) {
        return appRoleRepository.findByRoleCode(roleName)
                .map(role -> role.isEnabled() ? role.getPermissionLevel() : "VIEWER")
                .orElseGet(() -> switch (roleName) {
                    case "SUPER_ADMIN" -> "ADMIN";
                    case "WAREHOUSE_MANAGER" -> "MANAGER";
                    case "WAREHOUSE_OPERATOR" -> "OPERATOR";
                    default -> "VIEWER";
                });
    }
}
