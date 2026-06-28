package com.cap.server.controller;

import com.cap.server.entity.User;
import com.cap.server.security.JwtUser;
import com.cap.server.service.AppService;
import com.cap.server.service.PermissionService;
import com.cap.server.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final PermissionService permissionService;
    private final AppService appService;

    public UserController(UserService userService,
                          PermissionService permissionService,
                          AppService appService) {
        this.userService = userService;
        this.permissionService = permissionService;
        this.appService = appService;
    }

    @GetMapping("/user/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal JwtUser jwtUser) {
        return ResponseEntity.ok(Map.of(
                "id", jwtUser.getId(),
                "username", jwtUser.getUsername(),
                "role", jwtUser.getRole()
        ));
    }

    /** 用户自己修改密码 */
    @PutMapping("/user/password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal JwtUser jwtUser,
                                             @RequestBody Map<String, String> body) {
        try {
            String oldPassword = body.get("oldPassword");
            String newPassword = body.get("newPassword");
            if (oldPassword == null || newPassword == null || newPassword.length() < 4) {
                return ResponseEntity.badRequest().body(Map.of("error", "密码至少4位"));
            }
            userService.changePassword(jwtUser.getId(), oldPassword, newPassword);
            return ResponseEntity.ok(Map.of("message", "密码修改成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 当前用户的权限列表 */
    @GetMapping("/user/permissions")
    public ResponseEntity<?> myPermissions(@AuthenticationPrincipal JwtUser jwtUser) {
        var perms = permissionService.getByUser(jwtUser.getId());
        var result = perms.stream()
                .map(p -> {
                    var app = appService.findById(p.getAppId()).orElse(null);
                    return Map.<String, Object>of(
                            "id", p.getId(), "appId", p.getAppId(),
                            "appName", app != null ? app.getName() : "?",
                            "appType", app != null ? app.getAppType() : "",
                            "status", p.getStatus()
                    );
                }).toList();
        return ResponseEntity.ok(result);
    }

    // ====== 管理员接口 ======

    @GetMapping("/admin/users")
    public ResponseEntity<List<User>> listUsers() {
        return ResponseEntity.ok(userService.listAll());
    }

    @PostMapping("/admin/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            String email = body.get("email");
            String nickname = body.get("nickname");
            String role = body.get("role");
            if (username == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
            }
            User user = userService.createByAdmin(username, password, email, nickname, role);
            return ResponseEntity.ok(Map.of(
                    "message", "创建成功",
                    "user", Map.of("id", user.getId(), "username", user.getUsername(),
                            "nickname", user.getNickname(), "role", user.getRole())
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/admin/users/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        try {
            User user = userService.updateStatus(id, body.get("status"));
            return ResponseEntity.ok(Map.of("message", "更新成功", "status", user.getStatus()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/admin/users/{id}/password")
    public ResponseEntity<?> adminSetPassword(@PathVariable Long id,
                                               @RequestBody Map<String, String> body) {
        try {
            String newPassword = body.get("newPassword");
            if (newPassword == null || newPassword.length() < 4) {
                return ResponseEntity.badRequest().body(Map.of("error", "密码至少4位"));
            }
            userService.adminSetPassword(id, newPassword);
            return ResponseEntity.ok(Map.of("message", "密码已重置"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
