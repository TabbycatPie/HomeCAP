package com.cap.server.service;

import com.cap.server.entity.App;
import com.cap.server.entity.User;
import com.cap.server.entity.UserAppPermission;
import com.cap.server.repository.UserAppPermissionRepository;
import com.cap.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAppPermissionRepository permissionRepository;
    private final AppService appService;
    private final PluginManager pluginManager;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserAppPermissionRepository permissionRepository,
                       AppService appService,
                       PluginManager pluginManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionRepository = permissionRepository;
        this.appService = appService;
        this.pluginManager = pluginManager;
    }

    public User register(String username, String password, String email, String nickname) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setNickname(nickname != null ? nickname : username);
        user.setStatus("enabled");

        // 第一个注册的用户自动成为管理员
        if (userRepository.count() == 0) {
            user.setRole("admin");
        } else {
            user.setRole("user");
        }

        return userRepository.save(user);
    }

    public User authenticate(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        if (!"enabled".equals(user.getStatus())) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        return user;
    }

    public List<User> listAll() {
        return userRepository.findAll();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User updateStatus(Long userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setStatus(status);
        return userRepository.save(user);
    }

    public User createByAdmin(String username, String password, String email, String nickname, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setNickname(nickname != null ? nickname : username);
        user.setStatus("enabled");
        user.setRole(role != null ? role : "user");
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if ("admin".equals(user.getRole())) {
            throw new IllegalArgumentException("不能删除管理员账号");
        }

        // 先禁用所有关联 App 中的用户
        List<UserAppPermission> perms = permissionRepository.findByUserId(userId);
        for (UserAppPermission perm : perms) {
            try {
                App app = appService.findById(perm.getAppId()).orElse(null);
                if (app != null && !"direct".equals(app.getRedirectMode())) {
                    var plugin = pluginManager.getPlugin(app.getAppType());
                    plugin.deactivateUser(appService.toAppConfig(app), user.getUsername());
                    log.info("已在 {} 中禁用用户: {}", app.getName(), user.getUsername());
                }
            } catch (Exception e) {
                log.warn("在 App [{}] 中禁用用户失败: {}", perm.getAppId(), e.getMessage());
            }
        }

        // 删除权限记录
        permissionRepository.deleteAll(perms);

        // 删除用户
        userRepository.delete(user);
        log.info("用户 {} 已删除，共清理 {} 个权限", user.getUsername(), perms.size());
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void adminSetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
