package com.cap.plugin.model;

/**
 * 创建用户请求。
 */
public class CreateUserRequest {

    /** 平台用户名 — 尽量在各 App 间保持一致 */
    private String username;

    /** 显示昵称 */
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 初始密码（由平台生成或用户设置） */
    private String password;

    public CreateUserRequest() {}

    public CreateUserRequest(String username, String displayName, String email, String password) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
