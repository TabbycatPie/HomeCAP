package com.cap.plugin.calibre;

import com.cap.plugin.api.AppAuthPlugin;
import com.cap.plugin.model.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Calibre-web 认证插件。
 *
 * Calibre-web 没有完善的 REST API 用于用户管理，
 * 用户创建需要管理员手动在 Calibre-web 中操作。
 * SSO 通过表单自动提交到登录页面实现。
 *
 * 配置: adminUser + adminPassword
 */
public class CalibrePlugin implements AppAuthPlugin {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public String getAppType() { return "calibre"; }

    @Override
    public String getAppDisplayName() { return "Calibre"; }

    @Override
    public PluginResult createUser(AppConfig config, CreateUserRequest request) {
        // Calibre-web 没有公开的用户管理 API
        return PluginResult.fail("Calibre 不支持 API 自动创建用户，请手动在 Calibre-web 中创建用户");
    }

    @Override
    public SsoResult ssoLogin(AppConfig config, SsoRequest request) {
        String baseUrl = config.getBaseUrl().replaceAll("/$", "");
        String username = request.getUsername();
        String password = request.getUserPassword();

        if (password == null || password.isEmpty()) {
            return SsoResult.redirect(baseUrl + "/login");
        }

        // 服务端登录 calibre-web，获取 session cookie
        try {
            String formBody = "username=" + java.net.URLEncoder.encode(username, "UTF-8")
                    + "&password=" + java.net.URLEncoder.encode(password, "UTF-8")
                    + "&submit=";
            HttpRequest loginReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/login?next=%2F"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> loginResp = httpClient.send(loginReq, HttpResponse.BodyHandlers.ofString());

            // 提取 session cookie
            String setCookie = loginResp.headers().firstValue("Set-Cookie").orElse(null);
            if (setCookie != null && loginResp.statusCode() == 302) {
                String cookie = setCookie.split(";")[0]; // session=xxx
                String html = buildCookiePage(baseUrl, cookie);
                return SsoResult.html(html);
            }
        } catch (Exception e) {
            // 降级到 form_post
        }

        Map<String, String> fields = Map.of(
            "username", username != null ? username : "",
            "password", password,
            "submit", ""
        );
        return SsoResult.formPost(baseUrl + "/login?next=%2F", fields, baseUrl + "/");
    }

    private String buildCookiePage(String baseUrl, String cookie) {
        return "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">\n"
            + "<title>正在登录 Calibre...</title>\n"
            + "<style>body{background:#f5f7fa;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;font-family:sans-serif}"
            + ".box{text-align:center;padding:40px;background:#fff;border-radius:12px;box-shadow:0 4px 24px rgba(0,0,0,0.08)}"
            + ".spinner{width:40px;height:40px;margin:0 auto 16px;border:3px solid #e0e0e0;border-top-color:#c0392b;border-radius:50%;animation:s .8s linear infinite}"
            + "@keyframes s{to{transform:rotate(360deg)}}p{color:#909399;font-size:14px}</style></head><body>"
            + "<div class=\"box\"><div class=\"spinner\"></div><p>正在登录 Calibre，请稍候...</p></div>"
            + "<script>document.cookie='" + jsStr(cookie) + ";path=/;max-age=86400';"
            + "setTimeout(function(){window.location.href='" + jsStr(baseUrl) + "/';},600);</script>"
            + "</body></html>";
    }

    private String jsStr(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }

    @Override
    public PluginResult testConnection(AppConfig config) {
        try {
            String baseUrl = config.getBaseUrl().replaceAll("/$", "");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/login"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return PluginResult.ok("Calibre-web 连接正常");
            }
            return PluginResult.fail("HTTP " + resp.statusCode());
        } catch (java.net.http.HttpTimeoutException e) {
            return PluginResult.fail("连接超时");
        } catch (Exception e) {
            return PluginResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public boolean userExists(AppConfig config, String username) {
        return false; // 无 API 查询
    }

    @Override
    public PluginResult deactivateUser(AppConfig config, String username) {
        return PluginResult.fail("Calibre 不支持 API 管理用户");
    }
}
