package com.cap.server.controller;

import com.cap.server.entity.App;
import com.cap.server.entity.UserAppPermission;
import com.cap.server.service.AppService;
import com.cap.server.service.PermissionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/permissions")
public class PermissionController {

    private final PermissionService permissionService;
    private final AppService appService;

    public PermissionController(PermissionService permissionService,
                                AppService appService) {
        this.permissionService = permissionService;
        this.appService = appService;
    }

    /** 给用户分配 App 权限 */
    @PostMapping("/grant")
    public ResponseEntity<?> grant(@RequestBody Map<String, Long> body) {
        try {
            Long userId = body.get("userId");
            Long appId = body.get("appId");
            UserAppPermission perm = permissionService.grant(userId, appId);
            return ResponseEntity.ok(Map.of(
                    "message", "权限分配成功",
                    "permission", perm
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** 撤销用户对 App 的权限 */
    @PostMapping("/revoke")
    public ResponseEntity<?> revoke(@RequestBody Map<String, Long> body) {
        try {
            Long userId = body.get("userId");
            Long appId = body.get("appId");
            permissionService.revoke(userId, appId);
            return ResponseEntity.ok(Map.of("message", "权限撤销成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 当前登录用户获取自己的权限列表 */
    @GetMapping("/my")
    public ResponseEntity<?> getMyPermissions(@org.springframework.security.core.annotation.AuthenticationPrincipal com.cap.server.security.JwtUser jwtUser) {
        List<UserAppPermission> perms = permissionService.getByUser(jwtUser.getId());
        List<Map<String, Object>> result = perms.stream()
                .map(p -> {
                    App app = appService.findById(p.getAppId()).orElse(null);
                    return Map.<String, Object>of(
                            "id", p.getId(),
                            "appId", p.getAppId(),
                            "appName", app != null ? app.getName() : "未知",
                            "appType", app != null ? app.getAppType() : "未知",
                            "status", p.getStatus(),
                            "createdAt", p.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** 获取指定用户的权限列表 */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getByUser(@PathVariable Long userId) {
        List<UserAppPermission> perms = permissionService.getByUser(userId);
        List<Map<String, Object>> result = perms.stream()
                .map(p -> {
                    App app = appService.findById(p.getAppId()).orElse(null);
                    return Map.<String, Object>of(
                            "id", p.getId(),
                            "appId", p.getAppId(),
                            "appName", app != null ? app.getName() : "未知",
                            "appType", app != null ? app.getAppType() : "未知",
                            "status", p.getStatus(),
                            "createdAt", p.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** 获取指定 App 的权限分配列表 */
    @GetMapping("/app/{appId}")
    public ResponseEntity<List<UserAppPermission>> getByApp(@PathVariable Long appId) {
        return ResponseEntity.ok(permissionService.getByApp(appId));
    }
}
