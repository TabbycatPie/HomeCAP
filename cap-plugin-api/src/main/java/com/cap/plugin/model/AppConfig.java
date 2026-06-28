package com.cap.plugin.model;

import java.util.Map;

/**
 * App 配置信息 — 管理员在平台上配置每个 App 时填写的连接信息。
 */
public class AppConfig {

    /** 数据库中的 app ID */
    private Long appId;

    /** App 显示名称 */
    private String displayName;

    /** App 基础 URL */
    private String baseUrl;

    /** 各 App 特有的配置项，例如管理员账号密码、API Key 等 */
    private Map<String, String> properties;

    public AppConfig() {}

    public AppConfig(Long appId, String displayName, String baseUrl, Map<String, String> properties) {
        this.appId = appId;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.properties = properties;
    }

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }
}
