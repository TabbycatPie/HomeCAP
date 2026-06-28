package com.cap.plugin.navidrome;

import com.cap.plugin.api.AppAuthPlugin;
import com.cap.plugin.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Navidrome 认证插件
 *
 * Navidrome 是单页应用(SPA)，认证通过 /auth/login API 返回 JSON token，
 * token 存储在浏览器 localStorage 中，无传统的表单登录页面。
 *
 * SSO: 浏览器跳转到 /app 页面，用户需在 Navidrome 界面登录。
 * 连接测试: /rest/ping?f=json (Subsonic API)
 */
public class NavidromePlugin implements AppAuthPlugin {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override public String getAppType() { return "navidrome"; }
    @Override public String getAppDisplayName() { return "Navidrome"; }

    @Override
    public PluginResult createUser(AppConfig config, CreateUserRequest request) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String adminToken = getAdminToken(config);
            if (adminToken == null) {
                return PluginResult.fail("无法获取管理员 Token，请检查配置中的 adminUser/adminPassword 是否正确");
            }

            String body = mapper.writeValueAsString(java.util.Map.of(
                    "userName", request.getUsername(),
                    "password", request.getPassword(),
                    "name", request.getDisplayName(),
                    "isAdmin", false
            ));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/user"))
                    .header("Content-Type", "application/json")
                    .header("x-nd-authorization", "Bearer " + adminToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5)).build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 || resp.statusCode() == 201)
                return PluginResult.ok("用户创建成功");
            if (resp.statusCode() == 401)
                return PluginResult.fail("认证失败(HTTP 401)：管理员账号或密码错误");
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
        String baseUrl = config.getBaseUrl().replaceAll("/$", "");
        String username = request.getUsername();
        String password = request.getUserPassword();

        // 尝试后端登录获取 token，实现无感 SSO
        if (username != null && password != null && !password.isEmpty()) {
            try {
                String body = mapper.writeValueAsString(java.util.Map.of("username", username, "password", password));
                HttpRequest loginReq = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(5))
                        .build();
                HttpResponse<String> loginResp = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());
                if (loginResp.statusCode() == 200) {
                    JsonNode json = mapper.readTree(loginResp.body());
                    String token = json.path("token").asText();
                    if (!token.isEmpty()) {
                        String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Navidrome</title>"
                            + "<style>body{background:#1a1a2e;color:#fff;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;font-family:sans-serif}"
                            + ".s{width:40px;height:40px;margin:20px auto;border:3px solid #444;border-top-color:#6c5ce7;border-radius:50%;animation:r .8s linear infinite}"
                            + "@keyframes r{to{transform:rotate(360deg)}}p{color:#aaa;font-size:14px}</style></head><body>"
                            + "<div style=\"text-align:center\"><div class=\"s\"></div><p>正在登录...</p></div>"
                            + "<script>localStorage.setItem('token','" + token.replace("'","\\'") + "');"
                            + "setTimeout(function(){location.href='" + baseUrl.replace("'","\\'") + "/app';},300);</script></body></html>";
                        return SsoResult.html(html);
                    }
                }
            } catch (Exception e) {}
        }
        return SsoResult.redirect(baseUrl + "/app");
    }

    @Override
    public PluginResult testConnection(AppConfig config) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rest/ping?f=json"))
                    .GET().timeout(Duration.ofSeconds(5)).build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode json = mapper.readTree(resp.body());
                String type = json.path("subsonic-response").path("type").asText("");
                String ver = json.path("subsonic-response").path("serverVersion").asText("");

                if (!type.equalsIgnoreCase("navidrome")) {
                    return PluginResult.fail("该地址返回的不是 Navidrome（type: " + type + "），请检查 API 地址是否正确");
                }
                return PluginResult.ok("Navidrome " + ver + " 连接正常");
            }
            if (resp.statusCode() == 404) {
                return PluginResult.fail("该地址不是 Navidrome 服务(404)，请检查 API 地址和端口是否正确");
            }
            return PluginResult.fail("HTTP " + resp.statusCode());
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
            String token = getAdminToken(config);
            if (token == null) return false;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/user"))
                    .header("x-nd-authorization", "Bearer " + token)
                    .GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode users = mapper.readTree(resp.body());
                if (users.isArray()) {
                    for (JsonNode u : users)
                        if (username.equals(u.path("userName").asText())) return true;
                }
            }
            return false;
        } catch (Exception e) { return false; }
    }

    @Override
    public PluginResult deactivateUser(AppConfig config, String username) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String token = getAdminToken(config);
            if (token == null) return PluginResult.fail("无法获取管理员 Token");

            // 查用户列表找 ID
            HttpRequest listReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/user"))
                    .header("x-nd-authorization", "Bearer " + token)
                    .GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> listResp = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());
            String userId = null;
            if (listResp.statusCode() == 200) {
                JsonNode users = mapper.readTree(listResp.body());
                if (users.isArray()) {
                    for (JsonNode u : users) {
                        if (username.equals(u.path("userName").asText())) {
                            userId = u.path("id").asText();
                            break;
                        }
                    }
                }
            }
            if (userId == null) return PluginResult.fail("用户 " + username + " 在 Navidrome 中不存在");

            HttpRequest delReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/user/" + userId))
                    .header("x-nd-authorization", "Bearer " + token)
                    .DELETE().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> delResp = httpClient.send(delReq, HttpResponse.BodyHandlers.ofString());
            if (delResp.statusCode() == 200 || delResp.statusCode() == 204)
                return PluginResult.ok("已删除");
            return PluginResult.fail("HTTP " + delResp.statusCode());
        } catch (Exception e) {
            return PluginResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private String getAdminToken(AppConfig config) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            String adminUser = config.getProperties().get("adminUser");
            String adminPass = config.getProperties().get("adminPassword");
            if (adminUser == null || adminPass == null) return null;

            String body = mapper.writeValueAsString(java.util.Map.of("username", adminUser, "password", adminPass));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200)
                return mapper.readTree(resp.body()).path("token").asText();
            return null;
        } catch (Exception e) { return null; }
    }
}
