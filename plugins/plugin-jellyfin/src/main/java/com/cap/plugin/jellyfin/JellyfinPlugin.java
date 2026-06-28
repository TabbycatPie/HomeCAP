package com.cap.plugin.jellyfin;

import com.cap.plugin.api.AppAuthPlugin;
import com.cap.plugin.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Jellyfin 认证插件 v3
 *
 * SSO: 利用 Jellyfin 的 CORS 支持，浏览器端直接调用 API 获取 AccessToken，
 * 存储到 localStorage 后跳转 Web 界面，实现无感登录。
 *
 * 官方 API: https://api.jellyfin.org/
 */
public class JellyfinPlugin implements AppAuthPlugin {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String CLIENT_NAME = "CAP Unified Auth";

    @Override public String getAppType() { return "jellyfin"; }
    @Override public String getAppDisplayName() { return "Jellyfin"; }

    @Override
    public PluginResult createUser(AppConfig config, CreateUserRequest request) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String token = resolveToken(config);
            if (token == null)
                return PluginResult.fail("无法获取管理员 Token。请在配置中填写 apiKey，或 adminUser + adminPassword");

            String jsonBody = mapper.writeValueAsString(java.util.Map.of(
                    "Name", request.getUsername(), "Password", request.getPassword()));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/Users/New"))
                    .header("Content-Type", "application/json")
                    .header("X-Emby-Token", token)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(8)).build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) return PluginResult.ok("用户创建成功");
            if (resp.body() != null && resp.body().contains("already"))
                return PluginResult.ok("用户已存在");
            if (resp.statusCode() == 401)
                return PluginResult.fail("认证失败(HTTP 401)：请检查 apiKey 或管理员账号密码是否正确");
            return PluginResult.fail("HTTP " + resp.statusCode());
        } catch (java.net.http.HttpTimeoutException e) {
            return PluginResult.fail("连接超时：请检查 API 地址是否正确");
        } catch (java.net.ConnectException e) {
            return PluginResult.fail("连接被拒绝：请检查 API 地址和端口是否正确");
        } catch (Exception e) {
            return PluginResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public SsoResult ssoLogin(AppConfig config, SsoRequest request) {
        // Jellyfin localStorage 跨域不可写，直接跳转到 Web 界面让用户登录
        String baseUrl = config.getBaseUrl().replaceAll("/$", "");
        return SsoResult.redirect(baseUrl + "/web/index.html");
    }

    @Override
    public PluginResult testConnection(AppConfig config) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/System/Info/Public"))
                    .GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode json = mapper.readTree(resp.body());
                String ver = json.has("Version") ? json.get("Version").asText() : "?";
                return PluginResult.ok("Jellyfin " + ver + " 连接正常");
            }
            return PluginResult.fail("HTTP " + resp.statusCode() + "：该地址不是 Jellyfin 服务");
        } catch (java.net.http.HttpTimeoutException e) {
            return PluginResult.fail("连接超时(5s)：请检查 API 地址是否正确");
        } catch (java.net.ConnectException e) {
            return PluginResult.fail("连接被拒绝：请检查 API 地址和端口是否正确");
        } catch (Exception e) {
            return PluginResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public boolean userExists(AppConfig config, String username) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String token = resolveToken(config);
            if (token == null) return false;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/Users"))
                    .header("X-Emby-Token", token).GET()
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode users = mapper.readTree(resp.body());
                if (users.isArray()) {
                    for (JsonNode u : users)
                        if (username.equals(u.path("Name").asText())) return true;
                }
            }
            return false;
        } catch (Exception e) { return false; }
    }

    @Override
    public PluginResult deactivateUser(AppConfig config, String username) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String token = resolveToken(config);
            if (token == null) return PluginResult.fail("无法获取管理员 Token");

            // 查用户 ID
            HttpRequest listReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/Users"))
                    .header("X-Emby-Token", token).GET()
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> listResp = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());
            if (listResp.statusCode() != 200)
                return PluginResult.fail("获取用户列表失败: HTTP " + listResp.statusCode());

            JsonNode users = mapper.readTree(listResp.body());
            String userId = null;
            if (users.isArray()) {
                for (JsonNode u : users)
                    if (username.equals(u.path("Name").asText())) { userId = u.path("Id").asText(); break; }
            }
            if (userId == null) return PluginResult.fail("用户 " + username + " 不存在");

            HttpRequest delReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/Users/" + userId))
                    .header("X-Emby-Token", token).DELETE()
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> delResp = httpClient.send(delReq, HttpResponse.BodyHandlers.ofString());
            if (delResp.statusCode() == 200 || delResp.statusCode() == 204)
                return PluginResult.ok("用户已删除");
            return PluginResult.fail("HTTP " + delResp.statusCode());
        } catch (java.net.http.HttpTimeoutException e) {
            return PluginResult.fail("连接超时：" + e.getMessage());
        } catch (Exception e) {
            return PluginResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String resolveToken(AppConfig config) {
        String apiKey = config.getProperties().get("apiKey");
        if (apiKey != null && !apiKey.isEmpty()) return apiKey;
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String adminUser = config.getProperties().get("adminUser");
            String adminPass = config.getProperties().get("adminPassword");
            if (adminUser == null || adminPass == null) return null;

            String authHeader = String.format(
                    "MediaBrowser Client=\"%s\", Device=\"CAP\", DeviceId=\"cap-admin\", Version=\"1.0\"", CLIENT_NAME);
            String jsonBody = mapper.writeValueAsString(java.util.Map.of("Username", adminUser, "Pw", adminPass));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/Users/AuthenticateByName"))
                    .header("Content-Type", "application/json")
                    .header("X-Emby-Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200)
                return mapper.readTree(resp.body()).path("AccessToken").asText();
        } catch (Exception ignored) {}
        return null;
    }

    private String jsStr(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }
}
