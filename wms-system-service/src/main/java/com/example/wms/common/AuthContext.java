/**
 * 本文件实现 AuthContext 公共支撑模块。
 */
package com.example.wms.common;

import com.example.wms.domain.AppUser;

public final class AuthContext {
    private static final ThreadLocal<AppUser> CURRENT_USER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUser(AppUser user) {
        CURRENT_USER.set(user);
    }

    public static AppUser getUser() {
        AppUser user = CURRENT_USER.get();
        if (user == null) {
            throw new BusinessException("Not logged in");
        }
        return user;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
