package com.cap.server.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_app")
public class App {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 显示名称，如 "Nextcloud" */
    @Column(nullable = false, length = 100)
    private String name;

    /** 插件类型标识，对应 AppAuthPlugin.getAppType() */
    @Column(nullable = false, length = 50)
    private String appType;

    /** App 基础 URL */
    @Column(nullable = false, length = 255)
    private String baseUrl;

    /** 配置 JSON — 各 App 特有的连接配置 */
    @Column(columnDefinition = "TEXT")
    private String configJson;

    /** 用户访问的公网 URL（如 https://cloud.caliburn.work:8888） */
    @Column(length = 500)
    private String publicUrl;

    /** 跳转模式: plugin=插件SSO, direct=直接跳转 */
    @Column(length = 20)
    private String redirectMode = "plugin";

    /** 图标 URL */
    @Column(length = 500)
    private String iconUrl;

    /** enabled / disabled */
    @Column(nullable = false, length = 20)
    private String status = "enabled";

    /** 排序号 */
    private Integer sortOrder = 0;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters & Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAppType() { return appType; }
    public void setAppType(String appType) { this.appType = appType; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public String getRedirectMode() { return redirectMode; }
    public void setRedirectMode(String redirectMode) { this.redirectMode = redirectMode; }

    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
