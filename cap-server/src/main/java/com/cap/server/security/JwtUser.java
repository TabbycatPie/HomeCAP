package com.cap.server.security;

/**
 * 安全上下文中的用户信息。
 */
public class JwtUser {

    private Long id;
    private String username;
    private String role;

    public JwtUser(Long id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
}
