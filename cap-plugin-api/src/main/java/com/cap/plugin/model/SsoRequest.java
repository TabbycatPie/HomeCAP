package com.cap.plugin.model;

/**
 * SSO 单点登录请求。
 */
public class SsoRequest {

    /** 平台用户名 */
    private String username;

    /** 该用户在目标 App 中注册时设置/生成的密码或 token */
    private String userPassword;

    public SsoRequest() {}

    public SsoRequest(String username, String userPassword) {
        this.username = username;
        this.userPassword = userPassword;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserPassword() { return userPassword; }
    public void setUserPassword(String userPassword) { this.userPassword = userPassword; }
}
