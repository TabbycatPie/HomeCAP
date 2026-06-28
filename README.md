# Central Auth Platform (CAP) — 统一认证平台

一个中心化的用户认证与授权系统，通过插件机制实现 Nextcloud、Jellyfin、Navidrome、Calibre、Proxmox VE 等应用的统一登录和用户管理。

## 功能

- **用户系统**：注册 / 登录 / 修改密码 / 角色管理（JWT + BCrypt）
- **应用管理**：添加 / 编辑 / 禁用 / 测试连接，支持插件扩展
- **权限分配**：授权时自动在目标 App 创建账户，撤销/删除时自动禁用
- **SSO 单点登录**：
  - Nextcloud — Login Flow v2（弹窗授权，无感登录）
  - Jellyfin — 直接跳转 Web 界面
  - Navidrome — 跳转 SPA 应用
  - Calibre — 表单自动提交登录
  - Proxmox VE — Ticket 认证 SSO
  - xBoard — Token 认证
  - Forward — 通用页面穿透跳转
- **图标库**：30+ 扁平化图标，每应用独立选择，支持上传自定义图标
- **访问日志**：完整请求日志（含 headers、客户端 IP、JWT 用户）

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21 + Spring Boot 3.4 |
| 安全 | JWT (jjwt 0.12) + Spring Security |
| 数据库 | MySQL 8.0 (JPA / Hibernate) |
| 前端 | Vue 3 + Element Plus + Vite |
| 状态管理 | Pinia |
| 图标 | @tabler/icons-vue + 内置 SVG |
| 构建 | Maven |

## 项目结构

```
central-auth-platform/
├── pom.xml                          # 根 POM
├── cap-plugin-api/                  # 插件 API 接口
│   └── src/.../api/AppAuthPlugin.java
├── cap-server/                      # 主服务
│   └── src/main/java/com/cap/server/
│       ├── controller/              # REST API
│       ├── service/                 # 业务逻辑（含 PluginManager + 内置插件）
│       ├── entity/                  # JPA 实体
│       ├── security/                # JWT 过滤器
│       └── resources/application.yml
├── cap-web/                         # Vue 3 前端
│   └── src/views/
│       ├── Login.vue / Register.vue
│       ├── Dashboard.vue            # 用户首页（我的应用）
│       ├── Layout.vue               # 主布局
│       └── admin/                   # 管理页面
├── plugins/                         # 外部插件
│   ├── plugin-nextcloud/
│   ├── plugin-jellyfin/
│   ├── plugin-navidrome/
│   ├── plugin-calibre/
│   └── plugin-xboard/
└── docs/
    ├── PROJECT_ARCHIVE.md           # 详细项目文档
    ├── schema.sql                   # 数据库建表脚本
    ├── start.sh                     # 快速启动脚本
    └── cap.service                  # systemd 服务文件
```

## 快速开始

### 环境要求

- Java 21+
- Maven 3.8+
- Node.js 20+
- MySQL 8.0+

### 1. 创建数据库

```sql
CREATE DATABASE central_auth_platform
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

### 2. 配置数据库

编辑 `cap-server/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://your-mysql-host:3306/central_auth_platform?sslMode=DISABLED&...
    username: your_user
    password: your_password
```

### 3. 构建 & 启动

```bash
# 编译
mvn clean package -DskipTests

# 构建前端
cd cap-web && npm install && npm run build

# 启动后端
java -jar cap-server/target/cap-server-1.0.0-SNAPSHOT.jar
```

### 4. 配置 Nginx

```nginx
server {
    listen 80;
    root /path/to/cap-web/dist;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
    }

    location / {
        try_files $uri /index.html;
    }
}
```

### 5. 访问

打开 `http://localhost`，默认管理员：`admin / admin123`

## API 接口

### 认证（公开）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/register` | 用户注册 |

### 用户
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/user/me` | 当前用户信息 |
| PUT | `/api/user/password` | 修改密码 |
| GET | `/api/user/permissions` | 我的权限 |
| GET | `/api/user/apps` | 可用应用列表 |

### 管理员
| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST | `/api/admin/users` | 用户管理 |
| GET/POST/PUT | `/api/admin/apps` | 应用管理 |
| POST | `/api/admin/permissions/grant` | 分配权限 |
| POST | `/api/admin/permissions/revoke` | 撤销权限 |

### SSO
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/sso/access/{appId}` | 发起 SSO |
| GET | `/api/sso/go/{appId}` | SSO 页面（含轮询/表单） |
| GET | `/api/sso/poll/{pollId}` | Login Flow 轮询代理 |

## 插件开发

实现 `AppAuthPlugin` 接口即可添加新应用支持：

```java
public interface AppAuthPlugin {
    String getAppType();
    String getAppDisplayName();
    PluginResult createUser(AppConfig config, CreateUserRequest request);
    SsoResult ssoLogin(AppConfig config, SsoRequest request);
    PluginResult testConnection(AppConfig config);
    boolean userExists(AppConfig config, String username);
    PluginResult deactivateUser(AppConfig config, String username);
}
```

编译后的 JAR 放入 `cap-server/plugins/` 目录即可自动加载。

## 部署到生产

```bash
# 复制 systemd 服务文件
sudo cp docs/cap.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable cap
sudo systemctl start cap

# 查看日志
journalctl -u cap -f
```

## License

MIT
