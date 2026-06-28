package com.cap.server.service;

import com.cap.plugin.api.AppAuthPlugin;
import com.cap.plugin.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * 插件管理器 — 加载 JAR 插件 + 注册内置插件。
 */
@Service
public class PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    @Value("${app.plugin.scan-path:plugins/}")
    private String scanPath;

    private final Map<String, AppAuthPlugin> plugins = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadPlugins();
        // 注册内置插件（无需 JAR）
        registerBuiltin(new ForwardPlugin());
        registerBuiltin(new PvePlugin());
        log.info("总共 {} 个插件可用: {}", plugins.size(), plugins.keySet());
    }

    public void loadPlugins() {
        Path dir = Paths.get(scanPath);
        if (!Files.exists(dir)) {
            log.warn("插件目录不存在: {}", dir.toAbsolutePath());
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jarPath : stream) loadJar(jarPath.toFile());
        } catch (Exception e) {
            log.error("扫描插件目录失败", e);
        }
        log.info("已加载 {} 个 JAR 插件: {}", plugins.size(), plugins.keySet());
    }

    private void loadJar(File jarFile) {
        try {
            URL jarUrl = jarFile.toURI().toURL();
            try (URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl}, getClass().getClassLoader())) {
                List<Class<?>> candidates = new ArrayList<>();
                try (JarInputStream jis = new JarInputStream(Files.newInputStream(jarFile.toPath()))) {
                    JarEntry entry;
                    while ((entry = jis.getNextJarEntry()) != null) {
                        String name = entry.getName();
                        if (name.endsWith(".class") && !name.contains("module-info")) {
                            String className = name.replace('/', '.').replace(".class", "");
                            try {
                                Class<?> clazz = loader.loadClass(className);
                                if (AppAuthPlugin.class.isAssignableFrom(clazz) && !clazz.isInterface()
                                        && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                                    candidates.add(clazz);
                                }
                            } catch (NoClassDefFoundError e) {
                                log.debug("跳过 {}: {}", name, e.getMessage());
                            }
                        }
                    }
                }
                for (Class<?> clazz : candidates) {
                    AppAuthPlugin plugin = (AppAuthPlugin) clazz.getDeclaredConstructor().newInstance();
                    registerBuiltin(plugin);
                    log.info("加载插件: {} ({})", plugin.getAppType(), jarFile.getName());
                }
            }
        } catch (Exception e) {
            log.error("加载插件 JAR 失败: {}", jarFile.getName(), e);
        }
    }

    private void registerBuiltin(AppAuthPlugin plugin) {
        String type = plugin.getAppType();
        if (plugins.containsKey(type)) {
            log.warn("插件类型重复: {}，已覆盖", type);
        }
        plugins.put(type, plugin);
    }

    public AppAuthPlugin getPlugin(String appType) {
        AppAuthPlugin plugin = plugins.get(appType);
        if (plugin == null) {
            throw new IllegalArgumentException("不支持的 App 类型: " + appType
                    + "，可用类型: " + plugins.keySet());
        }
        return plugin;
    }

    public boolean hasPlugin(String appType) {
        return plugins.containsKey(appType);
    }

    public Map<String, AppAuthPlugin> getAllPlugins() {
        return Collections.unmodifiableMap(plugins);
    }

    // ================ 内置插件 ================

    /** 通用页面穿透跳转 — 直接跳转到目标页面，无用户管理 */
    static class ForwardPlugin implements AppAuthPlugin {
        @Override public String getAppType() { return "forward"; }
        @Override public String getAppDisplayName() { return "通用页面穿透"; }

        @Override
        public PluginResult createUser(AppConfig config, CreateUserRequest request) {
            return PluginResult.fail("通用跳转不支持用户管理");
        }

        @Override
        public SsoResult ssoLogin(AppConfig config, SsoRequest request) {
            String url = config.getBaseUrl();
            return SsoResult.redirect(url != null ? url : "/");
        }

        @Override
        public PluginResult testConnection(AppConfig config) {
            String url = config.getBaseUrl();
            if (url == null || url.isEmpty())
                return PluginResult.fail("请配置目标 URL");
            return PluginResult.ok("已配置跳转地址: " + url);
        }

        @Override public boolean userExists(AppConfig config, String username) { return false; }
        @Override public PluginResult deactivateUser(AppConfig config, String username) {
            return PluginResult.fail("通用跳转不支持用户管理");
        }
    }

    /** Proxmox VE 插件 — 支持 ticket 认证实现 SSO */
    static class PvePlugin implements AppAuthPlugin {
        private final HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8)).build();

        @Override public String getAppType() { return "pve"; }
        @Override public String getAppDisplayName() { return "Proxmox VE"; }

        @Override
        public PluginResult createUser(AppConfig config, CreateUserRequest request) {
            return PluginResult.fail("PVE 不支持 API 创建用户，请在 PVE 中手动创建");
        }

        @Override
        public SsoResult ssoLogin(AppConfig config, SsoRequest request) {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String username = request.getUsername();
            String password = request.getUserPassword();

            if (username == null || password == null)
                return SsoResult.redirect(baseUrl);

            try {
                // 获取 PVE ticket: POST /api2/json/access/ticket
                String body = "username=" + java.net.URLEncoder.encode(username, "UTF-8")
                        + "&password=" + java.net.URLEncoder.encode(password, "UTF-8");
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api2/json/access/ticket"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(5))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    var json = mapper.readTree(resp.body());
                    String ticket = json.path("data").path("ticket").asText();
                    String csrfToken = json.path("data").path("CSRFPreventionToken").asText();
                    String pveUsername = json.path("data").path("username").asText();

                    if (ticket != null && !ticket.isEmpty() && pveUsername != null) {
                        // PVE 支持 ticket URL 参数自动登录
                        String loginUrl = baseUrl
                                + "/?ticket=" + java.net.URLEncoder.encode(ticket, "UTF-8")
                                + "&username=" + java.net.URLEncoder.encode(pveUsername, "UTF-8");
                        log.info("PVE ticket 获取成功，SSO 跳转: {}", loginUrl);
                        return SsoResult.redirect(loginUrl);
                    }
                }
            } catch (Exception e) {
                log.warn("PVE ticket 获取失败: {}", e.getMessage());
            }
            return SsoResult.redirect(baseUrl);
        }

        @Override
        public PluginResult testConnection(AppConfig config) {
            try {
                String baseUrl = config.getBaseUrl().replaceAll("/$", "");
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api2/json/version"))
                        .GET().timeout(Duration.ofSeconds(5)).build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    var json = mapper.readTree(resp.body());
                    String version = json.path("data").path("version").asText("unknown");
                    return PluginResult.ok("PVE " + version + " 连接正常");
                }
                return PluginResult.ok("PVE 地址可访问 (HTTP " + resp.statusCode() + ")");
            } catch (java.net.http.HttpTimeoutException e) {
                return PluginResult.fail("连接超时：请检查 PVE 地址是否正确");
            } catch (java.net.ConnectException e) {
                return PluginResult.fail("连接被拒绝：请检查 PVE 地址和端口是否正确（默认 8006）");
            } catch (Exception e) {
                return PluginResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        @Override public boolean userExists(AppConfig config, String username) { return false; }
        @Override public PluginResult deactivateUser(AppConfig config, String username) {
            return PluginResult.fail("PVE 不支持 API 管理用户");
        }
    }
}
