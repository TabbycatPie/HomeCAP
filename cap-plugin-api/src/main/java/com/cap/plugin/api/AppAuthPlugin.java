package com.cap.plugin.api;

import com.cap.plugin.model.*;

/**
 * 统一 App 认证插件接口。
 * 每个需要接入统一认证的 App 都需要实现此接口。
 */
public interface AppAuthPlugin {

    /**
     * 插件标识，如 "nextcloud"、"jellyfin"、"navidrome"、"xboard"
     */
    String getAppType();

    /**
     * 插件显示名称，如 "Nextcloud"
     */
    String getAppDisplayName();

    /**
     * 在目标 App 中创建用户。
     * 管理员分配权限时调用。
     */
    PluginResult createUser(AppConfig config, CreateUserRequest request);

    /**
     * SSO 单点登录 — 用户点击跳转时调用。
     * 返回登录凭据（重定向 URL 或 Cookie）。
     */
    SsoResult ssoLogin(AppConfig config, SsoRequest request);

    /**
     * 测试 App 连接是否正常（管理员配置 App 时用）。
     */
    PluginResult testConnection(AppConfig config);

    /**
     * 检查用户在目标 App 中是否存在。
     */
    boolean userExists(AppConfig config, String username);

    /**
     * 删除/禁用目标 App 中的用户（权限回收时）。
     */
    PluginResult deactivateUser(AppConfig config, String username);
}
