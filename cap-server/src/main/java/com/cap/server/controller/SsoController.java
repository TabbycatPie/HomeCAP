package com.cap.server.controller;

import com.cap.plugin.model.SsoResult;
import com.cap.server.security.JwtUtil;
import com.cap.server.security.JwtUser;
import com.cap.server.service.SsoService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sso")
public class SsoController {

    private final SsoService ssoService;
    private final JwtUtil jwtUtil;

    public SsoController(SsoService ssoService, JwtUtil jwtUtil) {
        this.ssoService = ssoService;
        this.jwtUtil = jwtUtil;
    }

    /** 前端 JSON API */
    @PostMapping("/access/{appId}")
    public ResponseEntity<?> access(@AuthenticationPrincipal JwtUser jwtUser,
                                    @PathVariable Long appId) {
        SsoResult result = ssoService.performSso(jwtUser.getId(), appId);

        if ("error".equals(result.getMode()))
            return ResponseEntity.badRequest().body(Map.of("error", result.getErrorMessage()));

        if ("login_flow".equals(result.getMode())) {
            // 注册 poll session，返回 pollId
            String pollId = ssoService.registerPollSession(
                    result.getPollEndpoint(), result.getPollToken(),
                    result.getTargetUrl());
            return ResponseEntity.ok(Map.of(
                    "mode", "login_flow",
                    "loginUrl", result.getRedirectUrl(),
                    "pollId", pollId,
                    "targetUrl", result.getTargetUrl()
            ));
        }

        if ("form_post".equals(result.getMode()))
            return ResponseEntity.ok(Map.of(
                    "mode", "form_post",
                    "formActionUrl", result.getFormActionUrl(),
                    "formFields", result.getFormFields(),
                    "targetUrl", result.getTargetUrl()
            ));

        if ("html".equals(result.getMode()))
            return ResponseEntity.ok(Map.of("mode", "html"));

        String url = result.getRedirectUrl() != null ? result.getRedirectUrl() : result.getAccessUrl();
        return ResponseEntity.ok(Map.of("mode", "redirect", "url", url != null ? url : ""));
    }

    /** 浏览器 SSO 端点 */
    @GetMapping(value = "/go/{appId}", produces = MediaType.TEXT_HTML_VALUE)
    public String go(@PathVariable Long appId,
                     @RequestParam(required = false) String token,
                     @AuthenticationPrincipal JwtUser jwtUser) {
        Long userId;
        if (jwtUser != null) userId = jwtUser.getId();
        else if (token != null && jwtUtil.validateToken(token)) userId = jwtUtil.getUserId(token);
        else return errorPage("未授权，请先登录");

        SsoResult result = ssoService.performSso(userId, appId);

        if ("error".equals(result.getMode()))
            return errorPage(result.getErrorMessage());

        if ("login_flow".equals(result.getMode())) {
            // 注册 poll session
            String pollId = ssoService.registerPollSession(
                    result.getPollEndpoint(), result.getPollToken(),
                    result.getTargetUrl());
            return loginFlowPage(result.getRedirectUrl(), pollId, result.getTargetUrl());
        }

        if ("form_post".equals(result.getMode()))
            return formPostPage(result);

        if ("html".equals(result.getMode()) && result.getHtmlContent() != null)
            return result.getHtmlContent();

        String url = result.getRedirectUrl() != null ? result.getRedirectUrl()
                : result.getAccessUrl() != null ? result.getAccessUrl() : "/";
        return redirectPage(url);
    }

    /** 后端代理轮询 — 浏览器调用 CAP（无跨域）→ CAP 调 NC（内网） */
    @GetMapping("/poll/{pollId}")
    public ResponseEntity<?> poll(@PathVariable String pollId) {
        SsoService.PollResult r = ssoService.proxyPoll(pollId);
        if (r.success) {
            return ResponseEntity.ok(Map.of("success", true, "targetUrl", r.targetUrl));
        }
        return ResponseEntity.ok(Map.of("success", false, "message", r.message));
    }

    // ====== HTML 渲染 ======

    private String loginFlowPage(String loginUrl, String pollId, String targetUrl) {
        return "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">\n"
            + "<title>正在登录 Nextcloud...</title>\n"
            + "<style>"
            + "body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#f5f7fa;}"
            + ".box{text-align:center;padding:40px;background:#fff;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);max-width:420px}"
            + ".spinner{width:40px;height:40px;margin:0 auto 16px;border:3px solid #e0e0e0;border-top-color:#0082c9;border-radius:50%;animation:s .8s linear infinite;}"
            + "@keyframes s{to{transform:rotate(360deg)}}"
            + "h3{margin:0 0 8px;color:#333}"
            + "p{color:#909399;font-size:14px;line-height:1.6;margin:4px 0}"
            + ".btn{display:inline-block;margin-top:16px;padding:10px 28px;background:#0082c9;color:#fff;border-radius:30px;text-decoration:none;font-size:14px;cursor:pointer}"
            + ".status{font-size:13px;color:#aaa;margin-top:16px;transition:color .3s}"
            + ".success{color:#67c23a}"
            + "</style></head><body>"
            + "<div class=\"box\">"
            + "<div class=\"spinner\" id=\"spinner\"></div>"
            + "<h3>登录 Nextcloud</h3>"
            + "<p>请在打开的页面中点击「登录」按钮</p>"
            + "<a class=\"btn\" href=\"" + escapeHtml(loginUrl) + "\" target=\"_nc\" id=\"loginBtn\">打开授权页面</a>"
            + "<p class=\"status\" id=\"status\">等待授权...</p>"
            + "</div>"
            + "<script>"
            + "var ncPopup=window.open('" + escapeHtml(loginUrl) + "','_nc','width=900,height=700');"
            + "if(!ncPopup){document.getElementById('status').textContent='请允许弹窗';}"
            + "var pid='" + pollId + "';"
            + "var target='" + escapeHtml(targetUrl) + "';"
            + "var s=document.getElementById('status');"
            + "var sp=document.getElementById('spinner');"
            + "var check=function(){"
            + "  fetch('/api/sso/poll/'+pid)"
            + "  .then(function(r){return r.json()})"
            + "  .then(function(d){"
            + "    if(d.success){"
            + "      s.textContent='✅ 授权成功 — 弹窗即 Nextcloud';"
            + "      s.className='status success';"
            + "      sp.style.borderTopColor='#67c23a';"
            + "      clearInterval(timer);"
            + "      // 弹窗已登录 NC，切到弹窗即可"
            + "      try{ncPopup.focus();}catch(e){}"
            + "      // 3秒后关闭本页"
            + "      setTimeout(function(){try{window.close();}catch(e){document.body.innerHTML='<div class=\"box\" style=\"text-align:center;padding:40px\"><h3>授权成功</h3><p>请切换到弹窗</p></div>';}},3000);"
            + "    }"
            + "  })"
            + "  .catch(function(){})"
            + "};"
            + "var timer=setInterval(check,1500);"
            + "</script></body></html>";
    }

    private String formPostPage(SsoResult result) {
        String action = escapeHtml(result.getFormActionUrl());
        StringBuilder inputs = new StringBuilder();
        if (result.getFormFields() != null) {
            for (Map.Entry<String, String> e : result.getFormFields().entrySet()) {
                if ("requesttoken".equals(e.getKey())) continue;
                inputs.append("<input type=\"hidden\" name=\"")
                        .append(escapeHtml(e.getKey())).append("\" value=\"")
                        .append(escapeHtml(e.getValue() != null ? e.getValue() : "")).append("\" />\n");
            }
        }

        return "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">\n"
                + "<title>正在登录...</title>\n"
                + "<style>body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#f5f7fa;color:#333;}"
                + ".box{text-align:center;padding:40px;background:#fff;border-radius:12px;box-shadow:0 4px 16px rgba(0,0,0,0.1);}"
                + ".spinner{width:40px;height:40px;margin:0 auto 16px;border:3px solid #e0e0e0;border-top-color:#409eff;border-radius:50%;animation:s .8s linear infinite;}"
                + "@keyframes s{to{transform:rotate(360deg)}}"
                + "p{font-size:14px;color:#909399;}</style>\n"
                + "</head><body>\n"
                + "<div class=\"box\">\n"
                + "<div class=\"spinner\"></div>\n"
                + "<p>正在登录，请稍候...</p>\n"
                + "<form id=\"f\" method=\"POST\" action=\"" + action + "\">\n"
                + inputs.toString()
                + "<input type=\"hidden\" name=\"requesttoken\" id=\"rt\" value=\"\" />\n"
                + "</form>\n"
                + "<script>\n"
                + "var c=new AbortController();\n"
                + "setTimeout(function(){c.abort()},5000);\n"
                + "fetch('" + action + "',{signal:c.signal})\n"
                + "  .then(function(r){return r.text()})\n"
                + "  .then(function(h){\n"
                + "    var m=h.match(/csrf_token[^>]*value=\"([^\"]+)\"/)||h.match(/name=\"csrf_token\"[^>]*value=\"([^\"]+)\"/)||h.match(/requesttoken[= ]+\"([^\"]+)\"/)||h.match(/data-requesttoken=\"([^\"]+)\"/);\n"
                + "    if(m) document.getElementById('rt').value=m[1];\n"
                + "    document.getElementById('f').submit();\n"
                + "  })\n"
                + "  .catch(function(){document.getElementById('f').submit();});\n"
                + "setTimeout(function(){\n"
                + "  var s=document.getElementById('fb');\n"
                + "  if(s)s.style.display='block';\n"
                + "},8000);\n"
                + "</script>\n"
                + "<p id=\"fb\" style=\"display:none;margin-top:20px\"><a href=\"" + escapeHtml(action) + "\">如未自动登录，点击这里手动打开</a></p>\n"
                + "</div></body></html>";
    }

    private String redirectPage(String url) {
        return "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">\n"
                + "<meta http-equiv=\"refresh\" content=\"0;url=" + escapeHtml(url) + "\">\n"
                + "<title>正在跳转...</title>\n"
                + "</head><body>\n"
                + "<p>正在跳转到 <a href=\"" + escapeHtml(url) + "\">" + escapeHtml(url) + "</a></p>\n"
                + "</body></html>";
    }

    private String errorPage(String message) {
        return "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>登录失败</title>\n"
                + "<style>body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#f5f7fa;}"
                + ".box{text-align:center;padding:40px;background:#fff;border-radius:12px;box-shadow:0 4px 16px rgba(0,0,0,0.1);}</style>\n"
                + "</head><body><div class=\"box\"><h2>😕 登录失败</h2><p>" + escapeHtml(message) + "</p></div></body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
