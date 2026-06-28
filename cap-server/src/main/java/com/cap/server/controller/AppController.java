package com.cap.server.controller;

import com.cap.plugin.api.AppAuthPlugin;
import com.cap.server.entity.App;
import com.cap.server.service.AppService;
import com.cap.server.service.PluginManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AppController {

    private final AppService appService;
    private final PluginManager pluginManager;

    public AppController(AppService appService, PluginManager pluginManager) {
        this.appService = appService;
        this.pluginManager = pluginManager;
    }

    /** 获取已安装的插件类型列表 */
    @GetMapping("/admin/plugin-types")
    public ResponseEntity<?> listPluginTypes() {
        Map<String, AppAuthPlugin> all = pluginManager.getAllPlugins();
        List<Map<String, String>> list = all.values().stream()
                .map(p -> Map.of("appType", p.getAppType(), "displayName", p.getAppDisplayName()))
                .toList();
        return ResponseEntity.ok(list);
    }

    /** 创建 App */
    @PostMapping("/admin/apps")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String appType = (String) body.get("appType");
            String baseUrl = (String) body.get("baseUrl");
            String publicUrl = (String) body.get("publicUrl");
            String iconUrl = (String) body.get("iconUrl");
            String redirectMode = (String) body.get("redirectMode");
            @SuppressWarnings("unchecked")
            Map<String, String> config = (Map<String, String>) body.get("config");

            App app = appService.create(name, appType, baseUrl, publicUrl, iconUrl, redirectMode, config);
            return ResponseEntity.ok(app);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 获取所有 App */
    @GetMapping("/admin/apps")
    public ResponseEntity<List<App>> listAll() {
        return ResponseEntity.ok(appService.listAll());
    }

    /** 普通用户：获取所有 App 列表（含 configJson 用于图标等信息） */
    @GetMapping("/user/apps")
    public ResponseEntity<List<App>> listAccessibleApps() {
        return ResponseEntity.ok(appService.listAll());
    }

    /** 测试 App 连接 */
    @PostMapping("/admin/apps/{id}/test")
    public ResponseEntity<?> testConnection(@PathVariable Long id) {
        try {
            var result = appService.testConnection(id);
            return ResponseEntity.ok(Map.of(
                    "success", result.isSuccess(),
                    "message", result.getMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 更新 App 状态 */
    @PutMapping("/admin/apps/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        try {
            App app = appService.updateStatus(id, body.get("status"));
            return ResponseEntity.ok(Map.of("message", "更新成功", "status", app.getStatus()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 更新 App */
    @PutMapping("/admin/apps/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String baseUrl = (String) body.get("baseUrl");
            String publicUrl = (String) body.get("publicUrl");
            String iconUrl = (String) body.get("iconUrl");
            String redirectMode = (String) body.get("redirectMode");
            @SuppressWarnings("unchecked")
            Map<String, String> config = (Map<String, String>) body.get("config");
            App app = appService.update(id, name, baseUrl, publicUrl, redirectMode, iconUrl, config);
            return ResponseEntity.ok(app);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
