package com.cap.server.service;

import com.cap.plugin.api.AppAuthPlugin;
import com.cap.plugin.model.AppConfig;
import com.cap.plugin.model.SsoRequest;
import com.cap.plugin.model.SsoResult;
import com.cap.server.entity.App;
import com.cap.server.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@Service
public class SsoService {

    private static final Logger log = LoggerFactory.getLogger(SsoService.class);

    private final AppService appService;
    private final UserService userService;
    private final PermissionService permissionService;
    private final PluginManager pluginManager;

    private final Map<String, PollSession> pollSessions = new ConcurrentHashMap<>();

    /** 信任所有证书的 SSLContext（用于内网自签名证书） */
    private static final SSLContext sslContext = createLenientSslContext();

    private static SSLContext createLenientSslContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }}, new java.security.SecureRandom());
            return ctx;
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new RuntimeException("无法创建 SSLContext", e);
        }
    }

    /** 公网主机 → 内网代理地址映射（用于登录流轮询，绕过 SWAG 直连后端） */
    private static final Map<String, String> POLL_PROXY_MAP = Map.of(
            "cloud.caliburn.work:8888", "http://10.10.10.21:81",
            "jellyfin.caliburn.work:8888", "http://10.10.10.21:8096"
    );

    public SsoService(AppService appService,
                      UserService userService,
                      PermissionService permissionService,
                      PluginManager pluginManager) {
        this.appService = appService;
        this.userService = userService;
        this.permissionService = permissionService;
        this.pluginManager = pluginManager;
    }

    public SsoResult performSso(Long userId, Long appId) {
        if (!permissionService.hasPermission(userId, appId))
            return SsoResult.error("没有该应用的访问权限");

        App app = appService.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("App 不存在"));
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if ("direct".equals(app.getRedirectMode())) {
            String url = app.getPublicUrl() != null ? app.getPublicUrl() : app.getBaseUrl();
            if (url == null || url.isEmpty())
                return SsoResult.error("直跳模式需配置跳转地址");
            log.info("直跳: userId={} appId={} → {}", userId, appId, url);
            return SsoResult.redirect(url);
        }

        String appPassword = permissionService.getAppPassword(userId, appId).orElse(null);
        AppAuthPlugin plugin = pluginManager.getPlugin(app.getAppType());
        AppConfig config = appService.toAppConfig(app);
        SsoRequest request = new SsoRequest(user.getUsername(), appPassword);

        try {
            SsoResult result = plugin.ssoLogin(config, request);

            // 替换公网地址
            String publicUrl = app.getPublicUrl();
            String baseUrl = app.getBaseUrl();
            if (publicUrl != null && !publicUrl.isEmpty() && !publicUrl.equals(baseUrl))
                replaceBaseUrl(result, baseUrl, publicUrl);

            return result;
        } catch (Exception e) {
            log.error("SSO 失败 appId={} userId={}: {}", appId, userId, e.getMessage());
            return SsoResult.error("SSO 失败: " + e.getMessage());
        }
    }

    /** 注册 Login Flow 轮询会话 */
    public String registerPollSession(String pollEndpoint, String pollToken, String targetUrl) {
        String pollId = java.util.UUID.randomUUID().toString().substring(0, 16);
        // 生成内网代理 URL（用于后端轮询，不走公网）
        String internalEndpoint = toInternalPollUrl(pollEndpoint);
        pollSessions.put(pollId, new PollSession(pollEndpoint, internalEndpoint, pollToken, targetUrl, System.currentTimeMillis()));

        log.info("注册 Poll: {} (pub={}, int={})", pollId, pollEndpoint, internalEndpoint);
        return pollId;
    }

    /** 代理轮询 — 浏览器 → CAP → NC(SWAG) */
    public PollResult proxyPoll(String pollId) {
        PollSession session = pollSessions.get(pollId);
        if (session == null)
            return new PollResult(false, "expired", null);

        try {
            var client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .sslContext(sslContext)
                    .build();
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            String jsonBody = mapper.writeValueAsString(Map.of("token", session.pollToken));
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(session.internalPollEndpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("Poll {} → {}: HTTP {} body={}", pollId, session.internalPollEndpoint,
                    resp.statusCode(), resp.body() != null ? resp.body().substring(0, Math.min(200, resp.body().length())) : "null");

            // 404 或 200 但无数据 → 等待确认
            if (resp.statusCode() == 404)
                return new PollResult(false, "waiting", null);

            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isEmpty()) {
                var json = mapper.readTree(resp.body());
                if (json.has("server") && json.has("loginName")) {
                    log.info("Poll 成功: {} → 已登录 {}", pollId, json.path("loginName").asText());
                    pollSessions.remove(pollId);
                    return new PollResult(true, "login success", session.targetUrl);
                }
                log.debug("Poll {} 返回了 200 但未包含 server/loginName: {}", pollId, resp.body());
                return new PollResult(false, "waiting", null);
            }
            return new PollResult(false, "waiting", null);
        } catch (Exception e) {
            log.warn("Poll {} 异常: {} ({})", pollId, e.getClass().getSimpleName(), e.getMessage());
            return new PollResult(false, "waiting", null);
        }
    }

    /** 将公网 poll 地址转为内网代理地址 */
    private String toInternalPollUrl(String publicUrl) {
        if (publicUrl == null) return null;
        for (Map.Entry<String, String> e : POLL_PROXY_MAP.entrySet()) {
            if (publicUrl.contains(e.getKey())) {
                return publicUrl.replace("https://" + e.getKey(), e.getValue())
                        .replace("http://" + e.getKey(), e.getValue());
            }
        }
        return publicUrl; // 没有映射则原样返回
    }

    /** 从 URL 提取 Host（用于设置 Host 头） */
    private String extractHost(String url) {
        if (url == null) return null;
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return null;
        }
    }

    private void replaceBaseUrl(SsoResult result, String baseUrl, String publicUrl) {
        if (baseUrl == null || publicUrl == null || baseUrl.equals(publicUrl)) return;
        // 使用 URI 组件替换，避免子串误匹配（如 IP:port 部分重合）
        if (result.getRedirectUrl() != null)
            result.setRedirectUrl(replaceUrlPrefix(result.getRedirectUrl(), baseUrl, publicUrl));
        if (result.getAccessUrl() != null)
            result.setAccessUrl(replaceUrlPrefix(result.getAccessUrl(), baseUrl, publicUrl));
        if (result.getFormActionUrl() != null)
            result.setFormActionUrl(replaceUrlPrefix(result.getFormActionUrl(), baseUrl, publicUrl));
        if (result.getTargetUrl() != null)
            result.setTargetUrl(replaceUrlPrefix(result.getTargetUrl(), baseUrl, publicUrl));
        log.info("SSO {}: {} → {}", result.getMode(), baseUrl, publicUrl);
    }

    /** 精确替换 URL 中的 base 部分（协议+主机+端口），避免子串误匹配 */
    private String replaceUrlPrefix(String url, String oldBase, String newBase) {
        if (url == null || !url.startsWith(oldBase)) return url;
        return newBase + url.substring(oldBase.length());
    }

    static class PollSession {
        final String originalPollEndpoint;
        final String internalPollEndpoint;
        final String pollToken;
        final String targetUrl;
        final long createdAt;

        PollSession(String original, String internal, String token, String targetUrl, long createdAt) {
            this.originalPollEndpoint = original;
            this.internalPollEndpoint = internal;
            this.pollToken = token;
            this.targetUrl = targetUrl;
            this.createdAt = createdAt;
        }
    }

    public static class PollResult {
        public final boolean success;
        public final String message;
        public final String targetUrl;
        public PollResult(boolean success, String message, String targetUrl) {
            this.success = success; this.message = message; this.targetUrl = targetUrl;
        }
    }
}
