package com.cap.plugin.nextcloud;

import com.cap.plugin.api.AppAuthPlugin;
import com.cap.plugin.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Nextcloud 认证插件
 *
 * SSO: Login Flow v2 (官方)
 *   1. POST /index.php/login/v2 → {login, poll: {endpoint, token}}
 *   2. 浏览器打开 login URL → 用户点确认
 *   3. 后端代理轮询 poll endpoint（内网）→ 拿到登录凭证
 *   4. 跳转到 NC 文件页面
 *
 * 用户管理: OCS API
 */
public class NextcloudPlugin implements AppAuthPlugin {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String getAppType() { return "nextcloud"; }
    @Override public String getAppDisplayName() { return "Nextcloud"; }

    @Override
    public PluginResult createUser(AppConfig config, CreateUserRequest request) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String adminUser = config.getProperties().get("adminUser");
            String adminPass = config.getProperties().get("adminPassword");
            if (adminUser == null || adminPass == null)
                return PluginResult.fail("请配置管理员用户名和密码");

            String formBody = String.format("userid=%s&password=%s&displayName=%s&email=%s",
                    encode(request.getUsername()), encode(request.getPassword()),
                    encode(request.getDisplayName()), encode(request.getEmail() != null ? request.getEmail() : ""));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/ocs/v1.php/cloud/users"))
                    .header("OCS-APIRequest", "true")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", basicAuth(adminUser, adminPass))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(8)).build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 || resp.statusCode() == 201)
                return PluginResult.ok("用户创建成功");
            if (resp.body() != null && resp.body().contains("already exists")) {
                // 用户已存在，确保是启用状态
                enableUser(baseUrl, adminUser, adminPass, request.getUsername());
                return PluginResult.ok("用户已存在，已启用");
            }
            return PluginResult.fail(String.format("HTTP %d", resp.statusCode()));
        } catch (Exception e) {
            return PluginResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public SsoResult ssoLogin(AppConfig config, SsoRequest request) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String userPassword = request.getUserPassword();

            // 有存储的 appPassword → 直接生成登录 URL（无需 Login Flow）
            if (userPassword != null && !userPassword.isEmpty() && userPassword.length() > 20) {
                return SsoResult.redirect(baseUrl + "/?user=" + encode(request.getUsername())
                        + "&password=" + encode(userPassword));
            }

            // 首次登录 → Login Flow v2
            HttpRequest flowReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/index.php/login/v2"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(""))
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = httpClient.send(flowReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode flow = mapper.readTree(resp.body());
                String loginUrl = flow.path("login").asText();
                String pollEndpoint = flow.path("poll").path("endpoint").asText();
                String pollToken = flow.path("poll").path("token").asText();
                if (!loginUrl.isEmpty()) {
                    return SsoResult.loginFlow(loginUrl, pollEndpoint, pollToken, baseUrl + "/");
                }
            }
            return SsoResult.error("HTTP " + resp.statusCode());
        } catch (Exception e) {
            return SsoResult.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public PluginResult testConnection(AppConfig config) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/status.php")).GET()
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode json = mapper.readTree(resp.body());
                String v = json.has("version") ? json.get("version").asText() : "unknown";
                return PluginResult.ok("Nextcloud " + v + " 连接正常");
            }
            return PluginResult.fail("HTTP " + resp.statusCode());
        } catch (Exception e) {
            return PluginResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override public boolean userExists(AppConfig config, String username) {
        try {
            String b = config.getBaseUrl().replaceAll("/$", "");
            String a = config.getProperties().get("adminUser");
            String p = config.getProperties().get("adminPassword");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(b + "/ocs/v1.php/cloud/users/" + encode(username)))
                    .header("OCS-APIRequest", "true").header("Authorization", basicAuth(a, p)).GET()
                    .timeout(Duration.ofSeconds(5)).build();
            return httpClient.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) { return false; }
    }

    @Override public PluginResult deactivateUser(AppConfig config, String username) {
        try {
            String b = config.getBaseUrl().replaceAll("/$", "");
            String a = config.getProperties().get("adminUser");
            String p = config.getProperties().get("adminPassword");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(b + "/ocs/v1.php/cloud/users/" + encode(username) + "/disable"))
                    .header("OCS-APIRequest", "true")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", basicAuth(a, p))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(8)).build();
            HttpResponse<String> r = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 ? PluginResult.ok("已禁用") : PluginResult.fail("HTTP " + r.statusCode());
        } catch (Exception e) { return PluginResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    private void enableUser(String baseUrl, String adminUser, String adminPass, String username) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/ocs/v1.php/cloud/users/" + encode(username) + "/enable"))
                    .header("OCS-APIRequest", "true")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", basicAuth(adminUser, adminPass))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5)).build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    private String basicAuth(String u, String p) {
        return "Basic " + Base64.getEncoder().encodeToString(((u != null ? u : "") + ":" + (p != null ? p : "")).getBytes());
    }

    private String encode(String v) {
        try { return URLEncoder.encode(v != null ? v : "", "UTF-8"); }
        catch (Exception e) { return ""; }
    }
}
