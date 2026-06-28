package com.cap.server.controller;

import com.cap.server.security.JwtUser;
import com.cap.server.security.JwtUtil;
import com.cap.server.service.UserService;
import com.cap.server.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OIDC Provider — 支持 Nextcloud user_oidc 插件对接 CAP 实现 SSO
 */
@RestController
public class OidcController {

    private static final Logger log = LoggerFactory.getLogger(OidcController.class);

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final SecureRandom random = new SecureRandom();

    // 授权码存储（code → auth info）
    private final Map<String, AuthCode> authCodes = new ConcurrentHashMap<>();

    public OidcController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    /** OIDC Discovery */
    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> discovery() {
        String base = "http://10.10.10.24/api/oidc";
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("issuer", base);
        cfg.put("authorization_endpoint", base + "/authorize");
        cfg.put("token_endpoint", base + "/token");
        cfg.put("userinfo_endpoint", base + "/userinfo");
        cfg.put("jwks_uri", base + "/jwks");
        cfg.put("scopes_supported", List.of("openid", "profile", "email"));
        cfg.put("response_types_supported", List.of("code"));
        cfg.put("grant_types_supported", List.of("authorization_code"));
        cfg.put("subject_types_supported", List.of("public"));
        cfg.put("id_token_signing_alg_values_supported", List.of("RS256", "HS256"));
        cfg.put("token_endpoint_auth_methods_supported", List.of("client_secret_post"));
        return ResponseEntity.ok(cfg);
    }

    /** Authorization endpoint */
    @GetMapping("/api/oidc/authorize")
    public String authorize(@RequestParam String client_id,
                            @RequestParam String redirect_uri,
                            @RequestParam String response_type,
                            @RequestParam String scope,
                            @RequestParam(required = false) String state,
                            @AuthenticationPrincipal JwtUser jwtUser) {
        if (jwtUser == null) {
            // 未登录 → 跳转登录页，登录后回到这里
            String thisUrl = "/api/oidc/authorize?client_id=" + enc(client_id) +
                "&redirect_uri=" + enc(redirect_uri) +
                "&response_type=" + enc(response_type) +
                "&scope=" + enc(scope) +
                (state != null ? "&state=" + enc(state) : "");
            return redirectToLogin(thisUrl);
        }

        // 已登录 → 生成授权码并跳回
        String code = generateCode();
        authCodes.put(code, new AuthCode(jwtUser.getId(), jwtUser.getUsername(), client_id, Instant.now()));

        String redirect = redirect_uri;
        if (redirect.contains("?")) {
            redirect += "&code=" + code;
        } else {
            redirect += "?code=" + code;
        }
        if (state != null) redirect += "&state=" + enc(state);

        log.info("OIDC authorize: user={} client={}", jwtUser.getUsername(), client_id);
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
            + "<meta http-equiv=\"refresh\" content=\"0;url=" + redirect + "\">"
            + "<title>正在跳转...</title></head><body>"
            + "<p>正在跳转到 <a href=\"" + redirect + "\">Nextcloud</a></p>"
            + "</body></html>";
    }

    /** Token endpoint */
    @PostMapping("/api/oidc/token")
    public ResponseEntity<?> token(@RequestParam String grant_type,
                                    @RequestParam String code,
                                    @RequestParam String client_id,
                                    @RequestParam String client_secret,
                                    @RequestParam String redirect_uri) {
        if (!"authorization_code".equals(grant_type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }

        AuthCode auth = authCodes.remove(code);
        if (auth == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_grant"));
        }

        // 生成 access_token (JWT) 和 id_token
        User user = userService.findById(auth.userId).orElse(null);
        String accessToken = jwtUtil.generateToken(auth.userId, auth.username,
                user != null && "admin".equals(user.getRole()) ? "admin" : "user");
        String idToken = buildIdToken(auth, user);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("access_token", accessToken);
        resp.put("token_type", "Bearer");
        resp.put("id_token", idToken);
        resp.put("expires_in", 86400);
        return ResponseEntity.ok(resp);
    }

    /** UserInfo endpoint */
    @GetMapping("/api/oidc/userinfo")
    public ResponseEntity<?> userinfo(@AuthenticationPrincipal JwtUser jwtUser) {
        if (jwtUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
        }
        User user = userService.findById(jwtUser.getId()).orElse(null);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("sub", String.valueOf(jwtUser.getId()));
        info.put("preferred_username", jwtUser.getUsername());
        info.put("name", user != null ? user.getNickname() : jwtUser.getUsername());
        info.put("email", user != null ? user.getEmail() : "");
        return ResponseEntity.ok(info);
    }

    /** JWKS (stub — user_oidc needs this but we return empty for now) */
    @GetMapping("/api/oidc/jwks")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(Map.of("keys", List.of()));
    }

    private String buildIdToken(AuthCode auth, User user) {
        // 简单的 JWT id_token
        String header = base64url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        long now = System.currentTimeMillis() / 1000;
        String payload = base64url("{\"iss\":\"http://10.10.10.24/api/oidc\"," +
            "\"sub\":\"" + auth.userId + "\"," +
            "\"aud\":\"" + auth.clientId + "\"," +
            "\"exp\":" + (now + 3600) + "," +
            "\"iat\":" + now + "," +
            "\"preferred_username\":\"" + auth.username + "\"}");
        String sig = base64url(hmacSha256(header + "." + payload, jwtUtil.getSecret()));
        return header + "." + payload + "." + sig;
    }

    private String generateCode() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private String redirectToLogin(String returnUrl) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
            + "<meta http-equiv=\"refresh\" content=\"0;url=/#/login?redirect=" + enc(returnUrl) + "\">"
            + "<title>请先登录</title></head><body><p>正在跳转登录...</p></body></html>";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8);
    }

    private static String base64url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec spec = new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(spec);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    static class AuthCode {
        final Long userId;
        final String username;
        final String clientId;
        final Instant createdAt;

        AuthCode(Long userId, String username, String clientId, Instant createdAt) {
            this.userId = userId;
            this.username = username;
            this.clientId = clientId;
            this.createdAt = createdAt;
        }
    }
}
