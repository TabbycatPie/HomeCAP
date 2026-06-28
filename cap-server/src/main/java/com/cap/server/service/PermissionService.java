package com.cap.server.service;

import com.cap.plugin.api.AppAuthPlugin;
import com.cap.plugin.model.CreateUserRequest;
import com.cap.plugin.model.PluginResult;
import com.cap.server.entity.App;
import com.cap.server.entity.User;
import com.cap.server.entity.UserAppPermission;
import com.cap.server.repository.UserAppPermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 用户-App 权限分配服务。
 * 分配权限时自动在目标 App 中创建用户，并保存生成的密码用于 SSO。
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final UserAppPermissionRepository permissionRepository;
    private final UserService userService;
    private final AppService appService;
    private final PluginManager pluginManager;

    public PermissionService(UserAppPermissionRepository permissionRepository,
                             UserService userService,
                             AppService appService,
                             PluginManager pluginManager) {
        this.permissionRepository = permissionRepository;
        this.userService = userService;
        this.appService = appService;
        this.pluginManager = pluginManager;
    }

    /**
     * 给用户分配 App 权限 — 必须在目标 App 中成功创建用户才授权。
     */
    @Transactional
    public UserAppPermission grant(Long userId, Long appId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        App app = appService.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("App 不存在"));

        // 检查是否已分配
        if (permissionRepository.findByUserIdAndAppId(userId, appId).isPresent()) {
            throw new IllegalArgumentException("该用户已有此 App 权限");
        }

        // 生成密码
        String appPassword = generatePassword();

        // 直跳模式不需要在目标 App 创建用户
        if ("direct".equals(app.getRedirectMode())) {
            UserAppPermission perm = new UserAppPermission();
            perm.setUserId(userId);
            perm.setAppId(appId);
            perm.setStatus("enabled");
            perm.setAppUserPassword(appPassword);
            log.info("直跳模式，无需在 {} 中创建用户", app.getName());
            return permissionRepository.save(perm);
        }

        // 通过插件在目标 App 中创建用户 — 失败则拒绝授权
        AppAuthPlugin plugin = pluginManager.getPlugin(app.getAppType());
        CreateUserRequest req = new CreateUserRequest(
                user.getUsername(),
                user.getNickname() != null ? user.getNickname() : user.getUsername(),
                user.getEmail() != null ? user.getEmail() : "",
                appPassword
        );
        PluginResult result;
        try {
            result = plugin.createUser(appService.toAppConfig(app), req);
        } catch (Exception e) {
            throw new RuntimeException("在 " + app.getName() + " 中创建用户异常: " + e.getMessage());
        }

        if (!result.isSuccess()) {
            throw new RuntimeException("无法在 " + app.getName() + " 中创建用户: " + result.getMessage()
                    + "。请检查 " + app.getName() + " 的管理员配置是否正确。");
        }

        log.info("已在 {} 中创建用户: {}", app.getName(), user.getUsername());

        // 存权限记录（含密码）
        UserAppPermission perm = new UserAppPermission();
        perm.setUserId(userId);
        perm.setAppId(appId);
        perm.setStatus("enabled");
        perm.setAppUserPassword(appPassword);
        return permissionRepository.save(perm);
    }

    /**
     * 撤销用户对某 App 的权限 — 同时尝试在目标 App 中禁用/删除用户。
     */
    @Transactional
    public void revoke(Long userId, Long appId) {
        UserAppPermission perm = permissionRepository.findByUserIdAndAppId(userId, appId)
                .orElseThrow(() -> new IllegalArgumentException("权限记录不存在"));

        App app = appService.findById(appId).orElse(null);
        if (app != null) {
            AppAuthPlugin plugin = pluginManager.getPlugin(app.getAppType());
            try {
                User user = userService.findById(userId).orElse(null);
                if (user != null) {
                    plugin.deactivateUser(appService.toAppConfig(app), user.getUsername());
                    log.info("已在 {} 中禁用用户: {}", app.getName(), user.getUsername());
                }
            } catch (Exception e) {
                log.warn("在 {} 中禁用用户失败: {}", app.getName(), e.getMessage());
            }
        }

        permissionRepository.delete(perm);
    }

    public List<UserAppPermission> getByUser(Long userId) {
        return permissionRepository.findByUserId(userId);
    }

    public List<UserAppPermission> getByApp(Long appId) {
        return permissionRepository.findByAppId(appId);
    }

    public boolean hasPermission(Long userId, Long appId) {
        return permissionRepository.existsByUserIdAndAppIdAndStatus(userId, appId, "enabled");
    }

    /** 获取用户在某 App 中的登录密码 */
    public Optional<String> getAppPassword(Long userId, Long appId) {
        return permissionRepository.findByUserIdAndAppId(userId, appId)
                .map(UserAppPermission::getAppUserPassword);
    }

    private String generatePassword() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
