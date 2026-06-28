package com.cap.plugin.model;

import java.util.Map;

/**
 * SSO 单点登录结果。
 */
public class SsoResult {

    private String mode;
    private String redirectUrl;
    private String formActionUrl;
    private Map<String, String> formFields;
    private String cookie;
    private String accessUrl;
    private String targetUrl;
    private String errorMessage;
    private String htmlContent;

    /** Login Flow 轮询端点（NC 内部） */
    private String pollEndpoint;
    /** Login Flow 轮询 token */
    private String pollToken;

    public SsoResult() {}

    public static SsoResult redirect(String url) {
        SsoResult r = new SsoResult();
        r.mode = "redirect";
        r.redirectUrl = url;
        r.accessUrl = url;
        r.targetUrl = url;
        return r;
    }

    public static SsoResult formPost(String actionUrl, Map<String, String> fields, String targetUrl) {
        SsoResult r = new SsoResult();
        r.mode = "form_post";
        r.formActionUrl = actionUrl;
        r.formFields = fields;
        r.targetUrl = targetUrl;
        return r;
    }

    public static SsoResult html(String html) {
        SsoResult r = new SsoResult();
        r.mode = "html";
        r.htmlContent = html;
        return r;
    }

    /** Login Flow v2: 返回跳转地址 + poll 信息 */
    public static SsoResult loginFlow(String loginUrl, String pollEndpoint, String pollToken, String targetUrl) {
        SsoResult r = new SsoResult();
        r.mode = "login_flow";
        r.redirectUrl = loginUrl;
        r.pollEndpoint = pollEndpoint;
        r.pollToken = pollToken;
        r.targetUrl = targetUrl;
        return r;
    }

    public static SsoResult error(String message) {
        SsoResult r = new SsoResult();
        r.mode = "error";
        r.errorMessage = message;
        return r;
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getFormActionUrl() { return formActionUrl; }
    public void setFormActionUrl(String formActionUrl) { this.formActionUrl = formActionUrl; }

    public Map<String, String> getFormFields() { return formFields; }
    public void setFormFields(Map<String, String> formFields) { this.formFields = formFields; }

    public String getCookie() { return cookie; }
    public void setCookie(String cookie) { this.cookie = cookie; }

    public String getAccessUrl() { return accessUrl; }
    public void setAccessUrl(String accessUrl) { this.accessUrl = accessUrl; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }

    public String getPollEndpoint() { return pollEndpoint; }
    public void setPollEndpoint(String pollEndpoint) { this.pollEndpoint = pollEndpoint; }

    public String getPollToken() { return pollToken; }
    public void setPollToken(String pollToken) { this.pollToken = pollToken; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
