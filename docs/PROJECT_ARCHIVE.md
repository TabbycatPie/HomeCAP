# Central Auth Platform (CAP) — 统一认证平台

## 项目归档文档

> **归档日期**: 2026-06-28  
> **最后更新**: 2026-06-28  
> **状态**: 开发中（MVP 阶段）

---

## 1. 项目概述

统一认证平台（Central Auth Platform，简称 CAP）是一个中心化用户认证与授权系统。用户在平台注册后，管理员可分配其对不同应用（Nextcloud、Jellyfin、Navidrome、Calibre、XBoard、PVE 等）的访问权限。平台通过插件机制，将用户自动创建到目标应用中，并实现 SSO（单点登录）跳转。

### 核心功能
| 功能 | 状态 | 说明 |
|------|------|------|
| 用户注册/登录 | ✅ 已完成 | JWT 认证，BCrypt 密码加密 |
| 应用管理（CRUD） | ✅ 已完成 | 支持添加/编辑/禁用/测试连接 |
| 权限分配/撤销 | ✅ 已完成 | 分配时自动在目标 App 创建用户 |
| 用户管理 | ✅ 已完成 | 管理员创建/删除/重置密码 |
| 修改密码 | ✅ 已完成 | 用户自行修改 + 管理员重置 |
| Nextcloud SSO | ⚠️ 有问题 | Login Flow v2，grant 后 token 轮询不稳定 |
| Jellyfin SSO | ⚠️ 有问题 | CORS API + localStorage，无法真正无感登录 |
| Navidrome SSO | ⚠️ 有问题 | 曾跳到错误的 nextcloud 地址，端口 9111 |
| Calibre SSO | ⚠️ 有问题 | form_post 提交，需验证是否正常工作 |
| 直跳模式（Forward） | ⚠️ 有问题 | 保存后 URL 不持久化 |
| PVE 插件 | ✅ 已完成 | 直接跳转，无用户管理 API |
| 图标上传 | ✅ 已完成 | 本地保存到 uploads/icons/ |
| 前端 UI | ⚠️ 有问题 | 侧边栏折叠图标过大、图标拉伸等 |

---

## 2. 技术架构

```
┌─────────────────────────────────────────────────────┐
│                    Nginx :80                        │
│         (Vue 前端 + API 反向代理)                     │
├─────────────────────────────────────────────────────┤
│  cap-web/ (Vue 3 + Element Plus)                    │
│  - dist/ 已构建的静态文件                            │
│  - 使用 Tabler Icons (@tabler/icons-vue)            │
├─────────────────────────────────────────────────────┤
│  cap-server/ (Spring Boot 3.4 + Java 21)            │
│  :8080                                               │
│  ├── SecurityConfig — JWT + Spring Security          │
│  ├── Controllers — Auth/User/App/Permission/SSO     │
│  ├── Services — UserService/AppService/SSOService   │
│  └── PluginManager — 加载 JAR + 内置插件              │
├─────────────────────────────────────────────────────┤
│  cap-plugin-api/ — 插件 API 接口模型                  │
│  plugins/ — 各 App 插件实现                           │
│  ├── plugin-nextcloud  (Login Flow v2)              │
│  ├── plugin-jellyfin    (CORS API auth)             │
│  ├── plugin-navidrome   (Subsonic API)              │
│  ├── plugin-calibre     (form_post)                 │
│  └── plugin-xboard      (stub)                      │
├─────────────────────────────────────────────────────┤
│  MySQL 10.10.10.12:3306                             │
│  Database: central_auth_platform                     │
│  User: cap_user / claudeloveu@@##                   │
└─────────────────────────────────────────────────────┘
```

### 技术栈
| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.4.0 |
| Java | OpenJDK | 21 |
| 安全认证 | JWT (jjwt) | 0.12.6 |
| 数据库 | MySQL | (utf8mb4) |
| ORM | JPA / Hibernate | (随 Spring Boot) |
| 前端框架 | Vue 3 + Vite | — |
| UI 库 | Element Plus | — |
| 图标库 | Tabler Icons | — |
| 构建工具 | Maven | — |

---

## 3. 环境信息

### 服务器

| 项目 | 详情 |
|------|------|
| 本机 IP | 10.10.10.28（推测） |
| MySQL 服务器 | 10.10.10.12:3306 |
| 应用服务器 (Docker) | 10.10.10.21 |
| Nextcloud | cloud.caliburn.work:8888 → 10.10.10.21:2443 (SWAG/SSL) |
| Jellyfin | jellyfin.caliburn.work:8888 → 10.10.10.21:2443 |
| Navidrome | 10.10.10.21:9111 (Docker, 容器名 NaviDrome) |
| PVE | 10.10.10.21 (Proxmox VE) |

### 数据库连接

```
Host: 10.10.10.12
Port: 3306
Database: central_auth_platform
User: cap_user
Password: claudeloveu@@##
```

### 应用启动方式

当前以进程方式运行：
```bash
java -jar cap-server/target/cap-server-1.0.0-SNAPSHOT.jar
# PID: 26932
```

Nginx 配置：
```bash
# 配置位置: /etc/nginx/sites-enabled/cap
# 80 端口 → 前端 dist/ + API 反向代理 → 127.0.0.1:8080
```

⚠️ **没有 systemd 服务**，重启机器后需要手动启动或创建服务文件。

---

## 4. 数据库表结构

### sys_user — 平台用户表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 ID |
| username | VARCHAR(50) UNIQUE | 用户名 |
| password | VARCHAR(255) | BCrypt 加密的密码 |
| email | VARCHAR(100) | 邮箱 |
| nickname | VARCHAR(50) | 昵称 |
| status | VARCHAR(20) | enabled / disabled |
| role | VARCHAR(20) | user / admin |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

⚠️ 默认管理员: admin / admin123（系统启动时自动创建）

### sys_app — 应用注册表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 ID |
| name | VARCHAR(100) | 显示名称 |
| app_type | VARCHAR(50) | 插件类型标识 (nextcloud/jellyfin/navidrome/calibre/pve/forward) |
| base_url | VARCHAR(255) | App 基础 URL (内网调用) |
| config_json | TEXT | JSON 格式的扩展配置 |
| public_url | VARCHAR(500) | 用户访问的公网 URL |
| redirect_mode | VARCHAR(20) | plugin=插件SSO / direct=直接跳转 |
| icon_url | VARCHAR(500) | 图标 URL 或本地路径 |
| status | VARCHAR(20) | enabled / disabled |
| sort_order | INT | 排序 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### sys_user_app_permission — 用户权限关联表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增 ID |
| user_id | BIGINT | 用户 ID |
| app_id | BIGINT | App ID |
| status | VARCHAR(20) | enabled / disabled |
| app_user_password | VARCHAR(255) | 用户在此 App 中的密码（平台生成） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

## 5. API 接口一览

### 认证接口 (公开)
| Method | Path | 说明 |
|--------|------|------|
| POST | /api/auth/login | 用户登录 → JWT |
| POST | /api/auth/register | 用户注册 → JWT |

### 用户接口 (需登录)
| Method | Path | 说明 |
|--------|------|------|
| GET | /api/user/me | 获取当前用户信息 |
| PUT | /api/user/password | 用户修改密码 |
| GET | /api/admin/users | [管理员] 用户列表 |
| POST | /api/admin/users | [管理员] 创建用户 |
| DELETE | /api/admin/users/{id} | [管理员] 删除用户 |
| PUT | /api/admin/users/{id}/status | [管理员] 禁用/启用 |
| PUT | /api/admin/users/{id}/password | [管理员] 重置密码 |

### 应用管理接口 (管理员)
| Method | Path | 说明 |
|--------|------|------|
| GET | /api/admin/plugin-types | 已安装的插件类型 |
| GET | /api/admin/apps | App 列表 |
| POST | /api/admin/apps | 创建 App |
| PUT | /api/admin/apps/{id} | 更新 App |
| PUT | /api/admin/apps/{id}/status | 更新状态 |
| POST | /api/admin/apps/{id}/test | 测试连接 |

### 权限接口 (管理员)
| Method | Path | 说明 |
|--------|------|------|
| POST | /api/admin/permissions/grant | 分配权限（自动在 App 中创建用户） |
| POST | /api/admin/permissions/revoke | 撤销权限（尝试在 App 中删除用户） |
| GET | /api/admin/permissions/user/{userId} | 查看用户权限 |

### SSO 接口
| Method | Path | 说明 |
|--------|------|------|
| POST | /api/sso/access/{appId} | 前端 JSON API — 发起 SSO |
| GET | /api/sso/go/{appId} | 浏览器 SSO 端点（HTML 页面） |
| GET | /api/sso/poll/{pollId} | Nextcloud Login Flow 轮询代理 |

### 上传接口
| Method | Path | 说明 |
|--------|------|------|
| POST | /api/upload/app-icon/{appId} | 上传 App 图标（≤1MB，仅图片） |

---

## 6. 插件系统详解

### 插件接口 (AppAuthPlugin)

```java
public interface AppAuthPlugin {
    String getAppType();       // 唯一标识: nextcloud/jellyfin/navidrome...
    String getAppDisplayName(); // 显示名称
    PluginResult createUser(AppConfig, CreateUserRequest);
    SsoResult ssoLogin(AppConfig, SsoRequest);
    PluginResult testConnection(AppConfig);
    boolean userExists(AppConfig, String username);
    PluginResult deactivateUser(AppConfig, String username);
}
```

### 现有插件一览

| 插件 | appType | SSO 方式 | 用户管理 | 状态 |
|------|---------|---------|---------|------|
| NextcloudPlugin | nextcloud | Login Flow v2 (OAuth grant) | ✅ OCS API | ⚠️ token 轮询问题 |
| JellyfinPlugin | jellyfin | CORS API → localStorage | ✅ AuthenticateByName | ⚠️ 无法完全无感登录 |
| NavidromePlugin | navidrome | 直接跳转 /app | ✅ Subsonic API | ⚠️ 曾跳错地址 |
| CalibrePlugin | calibre | form_post 表单提交 | ❌ 无 API | ⚠️ 待验证 |
| XBoardPlugin | xboard | (stub) | ❌ | 🔴 未实现 |
| ForwardPlugin | forward | 直接跳转目标 URL | ❌ | — |
| PvePlugin | pve | 直接跳转 | ❌ | — |

### Nextcloud Login Flow v2 流程

```
1. CAP 后端 POST /index.php/login/v2 → 获取 login URL + poll endpoint + token
2. 浏览器在弹出窗口中打开 login URL，用户在 Nextcloud 中点"登录"
3. 浏览器轮询 /api/sso/poll/{pollId} → CAP 后端代请求 NC 内网地址
4. NC 返回登录成功数据 → 浏览器跳转到 targetUrl
```

⚠️ **已知问题**: 
- Nextcloud 显示 "Connected" 后 CAP 轮询收不到确认
- 需要检查 SWAG/SSL 代理下 poll endpoint 的可达性
- 公网 → 内网代理映射在 SsoService.POLL_PROXY_MAP 中

---

## 7. 前端结构

### 路由
| 路径 | 组件 | 说明 |
|------|------|------|
| /login | Login.vue | 登录页 |
| /register | Register.vue | 注册页 |
| /dashboard | Dashboard.vue | 我的应用（用户首页） |
| /admin/users | admin/Users.vue | 用户管理 |
| /admin/apps | admin/Apps.vue | 应用管理 |
| /admin/permissions | admin/Permissions.vue | 权限分配 |

### 前端技术
- Vue 3 Composition API + `<script setup>`
- Element Plus UI 组件
- Tabler Icons (`@tabler/icons-vue`) — **需要本地化图标库**
- Axios 请求层
- Pinia 状态管理 (auth store)
- Hash 路由模式 (createWebHashHistory)

### 构建方式
```bash
cd cap-web/
npm run build    # 输出到 dist/
```

---

## 8. 构建与部署

### 编译整个项目
```bash
cd /root/central-auth-platform/
mvn clean package -DskipTests
```

### 启动后端
```bash
# 直接启动
java -jar cap-server/target/cap-server-1.0.0-SNAPSHOT.jar

# 推荐创建 systemd 服务
# sudo nano /etc/systemd/system/cap.service
```

### 前端开发
```bash
cd cap-web/
npm run dev     # 开发服务器
npm run build   # 生产构建
```

### Nginx 配置位置
```
主配置: /etc/nginx/nginx.conf
站点配置: /etc/nginx/sites-enabled/cap (在 http block 内)
前端文件: /root/central-auth-platform/cap-web/dist/
```

### 日志
- Nginx: `/var/log/nginx/access.log` 和 `error.log`
- 应用: Spring Boot 标准输出（当前进程 stdout）
- JPA SQL: 已启用 show-sql + format_sql

---

## 9. 已知问题清单

### 🔴 严重（影响核心功能）
1. **Nextcloud SSO 登录不通**: Login Flow v2 中，NC 显示已授权但 CAP 轮询不到结果，token/proxy polling 机制需排查
2. **应用编辑保存**: 点击编辑弹窗后应用名称消失，保存失败 — 可能是前端表单数据绑定问题
3. **直跳模式**: 配置保存后 URL 在数据库中没变化

### 🟡 中等
4. **Navidrome 端口**: 曾跳到 nextcloud 地址而不是 navidrome，需确认 baseUrl 配置（Docker 端口 9111）
5. **Jellyfin 无感登录**: 通过 localStorage 注入 token 的方式不够可靠，Jellyfin 官网没有官方 SSO
6. **侧边栏折叠按钮** 图标过大（`huge`）
7. **Nextcloud 图标** 有拉伸问题
8. **权限分配应改为弹窗方式**（当前可能不是统一的弹窗风格）
9. **UI 操作逻辑不统一** — 有些用弹窗，有些用内页

### 🟢 轻微
10. **图标库**: 应下载 Tabler Icons 到本地，避免 CDN 依赖（国内网络可能访问不了）
11. **测试按钮**: 点击后没有反馈/跳转
12. **日志**: 需要在访问时即时看到请求日志
13. **XBoard 插件**: 只有空壳，需要实现
14. **PVE 认证**: 仅跳转，无 PVE ticket 认证
15. **没有 systemd 服务**: 重启后 Java 进程不会自动启动

---

## 10. 关键文件索引

```
central-auth-platform/
├── pom.xml                          # 根 POM（多模块）
├── docs/
│   ├── schema.sql                   # 数据库建表参考
│   └── PROJECT_ARCHIVE.md           # 📄 本文档
├── cap-plugin-api/                  # 插件 API 接口定义
│   └── src/main/java/com/cap/plugin/
│       ├── api/AppAuthPlugin.java   # 核心插件接口
│       └── model/                   # 数据模型
├── cap-server/                      # 主服务
│   └── src/main/java/com/cap/server/
│       ├── CapApplication.java      # 启动入口
│       ├── config/
│       │   └── SecurityConfig.java  # Spring Security + JWT
│       ├── controller/
│       │   ├── AuthController.java  # 登录/注册
│       │   ├── UserController.java  # 用户管理
│       │   ├── AppController.java   # 应用管理
│       │   ├── PermissionController.java # 权限分配
│       │   ├── SsoController.java   # SSO 跳转 + HTML 页面
│       │   └── FileController.java  # 图标上传
│       ├── entity/                  # JPA 实体
│       ├── service/
│       │   ├── UserService.java
│       │   ├── AppService.java
│       │   ├── PermissionService.java
│       │   ├── SsoService.java      # SSO 核心（含 poll 代理）
│       │   └── PluginManager.java   # 插件加载 + 内置插件
│       └── security/                # JWT 工具类
│       └── resources/
│           └── application.yml      # 数据库/日志/端口配置
├── cap-web/                         # Vue 3 前端
│   ├── dist/                        # 已构建的静态文件
│   ├── src/
│   │   ├── api/index.js             # Axios + 所有 API 方法
│   │   ├── router/index.js          # 路由 + 守卫
│   │   ├── store/auth.js            # Pinia 认证状态
│   │   ├── components/AppIcon.vue   # App 图标组件
│   │   └── views/
│   │       ├── Login.vue / Register.vue
│   │       ├── Layout.vue           # 主布局（侧边栏+顶栏）
│   │       ├── Dashboard.vue        # 我的应用
│   │       └── admin/
│   │           ├── Users.vue        # 用户管理
│   │           ├── Apps.vue         # 应用管理
│   │           └── Permissions.vue  # 权限分配
│   ├── index.html
│   └── package.json
└── plugins/                         # 插件模块
    ├── plugin-nextcloud/            # Nextcloud (Login Flow v2)
    ├── plugin-jellyfin/             # Jellyfin (CORS API)
    ├── plugin-navidrome/            # Navidrome (Subsonic)
    ├── plugin-calibre/              # Calibre-web (form_post)
    └── plugin-xboard/               # XBoard (空壳)
```

---

## 11. 后续工作建议

### 优先级 P0 — 立即修复
1. 排查 Nextcloud Login Flow v2 的 token 轮询失败问题
2. 修复应用编辑时名称消失/保存失败
3. 修复直跳模式 URL 保存

### 优先级 P1 — 短期
4. 用统一弹窗重构用户管理/权限分配 UI
5. 修复侧边栏折叠图标过大
6. 修复图标拉伸问题
7. 下载 Tabler Icons 到本地

### 优先级 P2 — 中期
8. 完成 Navidrome 的 SSO（验证 Subsonic token + localStorage 注入）
9. 完善 Calibre 登录
10. 实现 XBoard 插件
11. 创建 systemd 服务文件
12. 添加全面访问日志

---

## 12. 快速恢复（换用户后）

### 恢复步骤
```bash
# 1. 确认 MySQL 可达
mysql -h 10.10.10.12 -P 3306 -u cap_user -p'claudeloveu@@##' central_auth_platform -e "SELECT 1"

# 2. 重新编译（如果代码有改动）
cd /root/central-auth-platform/
mvn clean package -DskipTests

# 3. 启动后端
java -jar cap-server/target/cap-server-1.0.0-SNAPSHOT.jar &

# 4. 检查 Nginx
nginx -t && systemctl restart nginx

# 5. 访问
# http://<本机IP>/
# 默认管理员: admin / admin123
```

### 前端重新构建
```bash
cd /root/central-auth-platform/cap-web/
# 如果 node_modules 不存在
npm install
# 构建
npm run build
```

---

*文档由 Claude Code 自动生成，请随项目进展同步更新。*
