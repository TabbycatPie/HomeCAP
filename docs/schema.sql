-- =========================================================
-- Central Auth Platform — 数据库初始化脚本
-- 数据库由 JPA ddl-auto=update 自动创建，此脚本供参考/手动使用
-- =========================================================

CREATE DATABASE IF NOT EXISTS central_auth_platform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE central_auth_platform;

-- 平台用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    email       VARCHAR(100),
    nickname    VARCHAR(50),
    status      VARCHAR(20)  NOT NULL DEFAULT 'enabled' COMMENT 'enabled / disabled',
    role        VARCHAR(20)  NOT NULL DEFAULT 'user'    COMMENT 'user / admin',
    created_at  DATETIME,
    updated_at  DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已注册的 App 表
CREATE TABLE IF NOT EXISTS sys_app (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL         COMMENT '显示名称',
    app_type    VARCHAR(50)  NOT NULL         COMMENT '插件类型标识，如 nextcloud',
    base_url    VARCHAR(255) NOT NULL         COMMENT 'App 基础 URL',
    config_json TEXT                          COMMENT 'JSON 格式的配置',
    icon_url    VARCHAR(500)                  COMMENT '图标图片 URL',
    status      VARCHAR(20)  NOT NULL DEFAULT 'enabled',
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME,
    updated_at  DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户-App 权限关联表
CREATE TABLE IF NOT EXISTS sys_user_app_permission (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT   NOT NULL,
    app_id            BIGINT   NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'enabled',
    app_user_password VARCHAR(255)           COMMENT '该用户在此 App 中的密码',
    created_at        DATETIME,
    updated_at        DATETIME,
    UNIQUE KEY uk_user_app (user_id, app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
