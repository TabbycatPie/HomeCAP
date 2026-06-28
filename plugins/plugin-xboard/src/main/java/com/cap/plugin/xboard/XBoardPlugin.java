package com.cap.plugin.xboard;

import com.cap.plugin.api.AppAuthPlugin;
import com.cap.plugin.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * xBoard 认证插件。
 *
 * xBoard 是一个运维面板，通过 REST API 管理用户。
 *
 * 用户管理：通过管理员 Token 调用 API 创建/禁用用户
 * SSO 登录：返回管理面板地址
 */
public class XBoardPlugin implements AppAuthPlugin {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getAppType() {
        return "xboard";
    }

    @Override
    public String getAppDisplayName() {
        return "xBoard";
    }

    @Override
    public PluginResult createUser(AppConfig config, CreateUserRequest request) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String adminToken = getAdminToken(config);
            if (adminToken == null) {
                return PluginResult.fail("无法获取管理员 Token");
            }

            // POST /api/v1/user/store (根据 xBoard API 调整)
            String body = mapper.writeValueAsString(Map.of(
                    "email", request.getEmail() != null ? request.getEmail() : request.getUsername() + "@cap.local",
                    "password", request.getPassword(),
                    "name", request.getDisplayName(),
                    "username", request.getUsername()
            ));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/user/store"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", adminToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200 || resp.statusCode() == 201) {
                return PluginResult.ok("用户创建成功");
            }

            return PluginResult.fail("创建失败: HTTP " + resp.statusCode());
        } catch (Exception e) {
            return PluginResult.fail("创建异常: " + e.getMessage());
        }
    }

    @Override
    public SsoResult ssoLogin(AppConfig config, SsoRequest request) {
        String baseUrl = config.getBaseUrl().replaceAll("/$", "");
        // 尝试通过用户名密码获取 token，实现无感登录
        if (request.getUsername() != null && request.getUserPassword() != null) {
            try {
                String body = mapper.writeValueAsString(Map.of(
                        "email", request.getUsername() + "@cap.local",
                        "password", request.getUserPassword()
                ));
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/v1/passport/auth"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(5))
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode json = mapper.readTree(resp.body());
                    String token = json.path("data").path("auth_data").asText();
                    if (token != null && !token.isEmpty()) {
                        // 将 token 作为 URL 参数传递（xBoard 支持 ?token= 参数自动登录）
                        return SsoResult.redirect(baseUrl + "?token=" + token);
                    }
                }
            } catch (Exception e) {
                // token 获取失败，回退到直接跳转
            }
        }
        return SsoResult.redirect(baseUrl);
    }

    @Override
    public PluginResult testConnection(AppConfig config) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String token = getAdminToken(config);
            if (token == null) return PluginResult.fail("无法获取管理员 Token");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/panel/info"))
                    .header("Authorization", token)
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                return PluginResult.ok("连接成功");
            }

            return PluginResult.fail("连接失败: HTTP " + resp.statusCode());
        } catch (Exception e) {
            return PluginResult.fail("连接异常: " + e.getMessage());
        }
    }

    @Override
    public boolean userExists(AppConfig config, String username) {
        // xBoard 暂时通过 API 尝试创建来检查（幂等）
        return false;
    }

    @Override
    public PluginResult deactivateUser(AppConfig config, String username) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String token = getAdminToken(config);
            if (token == null) return PluginResult.fail("无法获取管理员 Token");

            // PUT /api/v1/user/ban
            String body = mapper.writeValueAsString(Map.of(
                    "username", username
            ));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/user/ban"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", token)
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                return PluginResult.ok("用户已禁用");
            }
            return PluginResult.fail("禁用失败: HTTP " + resp.statusCode());
        } catch (Exception e) {
            return PluginResult.fail("禁用异常: " + e.getMessage());
        }
    }

    private String getAdminToken(AppConfig config) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String email = config.getProperties().get("adminEmail");
            String password = config.getProperties().get("adminPassword");
            if (email == null || password == null) return null;

            String body = mapper.writeValueAsString(Map.of(
                    "email", email,
                    "password", password
            ));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/passport/auth"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode json = mapper.readTree(resp.body());
                return json.path("data").path("auth_data").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
