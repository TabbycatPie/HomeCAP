package com.cap.server.service;

import com.cap.plugin.api.AppAuthPlugin;
import com.cap.plugin.model.AppConfig;
import com.cap.plugin.model.PluginResult;
import com.cap.server.entity.App;
import com.cap.server.repository.AppRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AppService {

    private static final Logger log = LoggerFactory.getLogger(AppService.class);

    private final AppRepository appRepository;
    private final PluginManager pluginManager;
    private final ObjectMapper objectMapper;

    public AppService(AppRepository appRepository,
                      PluginManager pluginManager,
                      ObjectMapper objectMapper) {
        this.appRepository = appRepository;
        this.pluginManager = pluginManager;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        List<App> all = appRepository.findAll();
        if (all.isEmpty()) {
            log.info("数据库中没有已注册的 App");
        } else {
            String appList = all.stream()
                    .map(a -> String.format("[%d] %s (type=%s, url=%s, status=%s)",
                            a.getId(), a.getName(), a.getAppType(), a.getBaseUrl(), a.getStatus()))
                    .collect(Collectors.joining(", "));
            log.info("数据库中的 App 列表: {}", appList);
        }
    }

    public App create(String name, String appType, String baseUrl, String publicUrl, String iconUrl, String redirectMode, Map<String, String> configProps) {
        if (!"direct".equals(redirectMode) && !pluginManager.hasPlugin(appType)) {
            String msg = "不支持的 App 类型，请先安装对应插件或使用直跳模式: " + appType;
            log.warn("创建 App 失败: {}", msg);
            throw new IllegalArgumentException(msg);
        }

        App app = new App();
        app.setName(name);
        app.setAppType(appType);
        app.setBaseUrl(baseUrl);
        app.setPublicUrl(publicUrl);
        app.setIconUrl(iconUrl);
        app.setRedirectMode(redirectMode != null ? redirectMode : "plugin");

        if (configProps != null && !configProps.isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(configProps);
                app.setConfigJson(json);
                log.debug("App 配置: {}", json);
            } catch (Exception e) {
                throw new RuntimeException("配置序列化失败", e);
            }
        }

        App saved = appRepository.save(app);
        log.info("创建 App [{}]: name={}, type={}, url={}, publicUrl={}, mode={}, icon={}", saved.getId(), name, appType, baseUrl, publicUrl, saved.getRedirectMode(), iconUrl);
        return saved;
    }

    public PluginResult testConnection(Long appId) {        App app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("App 不存在"));

        // 直跳模式 — 直接检查公网地址
        if ("direct".equals(app.getRedirectMode())) {            String url = app.getPublicUrl() != null ? app.getPublicUrl() : app.getBaseUrl();
            if (url == null || url.isEmpty()) {                return PluginResult.fail("直跳模式需要配置公网地址或 API 地址");
            }            return PluginResult.ok("直跳模式已就绪，目标地址: " + url);
        }
        AppAuthPlugin plugin = pluginManager.getPlugin(app.getAppType());
        AppConfig config = toAppConfig(app);

        return plugin.testConnection(config);
    }
    public List<App> listAll() {
        return appRepository.findAll();
    }

    public Optional<App> findById(Long id) {
        return appRepository.findById(id);
    }

    public App updateStatus(Long appId, String status) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("App 不存在"));
        app.setStatus(status);
        return appRepository.save(app);
    }

    public App update(Long appId, String name, String baseUrl, String publicUrl, String redirectMode, String iconUrl, Map<String, String> configProps) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("App 不存在"));

        log.info("更新 App [{}] {}: name={}, baseUrl={}, publicUrl={}, redirectMode={}, iconUrl={}, config={}",
                appId, app.getName(), name, baseUrl, publicUrl, redirectMode, iconUrl, configProps);

        if (name != null) app.setName(name);
        if (baseUrl != null) app.setBaseUrl(baseUrl);
        if (publicUrl != null) app.setPublicUrl(publicUrl);
        if (redirectMode != null) app.setRedirectMode(redirectMode);
        if (iconUrl != null) app.setIconUrl(iconUrl);
        if (configProps != null) {
            try {
                app.setConfigJson(objectMapper.writeValueAsString(configProps));
            } catch (Exception e) {
                throw new RuntimeException("配置序列化失败", e);
            }
        }

        App saved = appRepository.save(app);
        log.info("App [{}] 已更新: name={}, baseUrl={}, publicUrl={}, mode={}, icon={}",
                saved.getId(), saved.getName(), saved.getBaseUrl(), saved.getPublicUrl(), saved.getRedirectMode(), saved.getIconUrl());
        return saved;
    }

    public AppConfig toAppConfig(App app) {
        Map<String, String> props = Map.of();
        if (app.getConfigJson() != null && !app.getConfigJson().isEmpty()) {
            try {
                props = objectMapper.readValue(app.getConfigJson(),
                        new TypeReference<Map<String, String>>() {});
            } catch (Exception ignored) {}
        }

        return new AppConfig(app.getId(), app.getName(), app.getBaseUrl(), props);
    }

    public App updateIcon(Long appId, String iconUrl) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new IllegalArgumentException("App 不存在"));
        app.setIconUrl(iconUrl);
        App saved = appRepository.save(app);
        log.info("App [{}] 图标已更新: {}", appId, iconUrl);
        return saved;
    }
}
