# PublicIPUpload - DDNS 动态域名解析管理系统

## 项目简介

PublicIPUpload 是一个基于 Spring Boot 的 DDNS（动态域名解析）管理系统，提供 Web 可视化界面，支持自动获取公网 IPv4/IPv6 地址并更新 DNS 解析记录。支持腾讯云和阿里云两大云服务商。

## 功能特点

### IP 获取
- **多源 IPv4 获取**：内置多个公网 IPv4 获取服务（Amazon、icanhazip、ifconfig.me 等）
- **多源 IPv6 获取**：内置多个公网 IPv6 获取服务（icanhazip-v6、ident.me-v6、ifconfig.co 等）
- **本地网卡监控**：支持监控本地网卡的 IPv4/IPv6 地址变化
- **自定义服务**：支持添加自定义 IP 获取服务 URL

### DDNS 功能
- **多云服务商支持**：支持腾讯云 DNS 和阿里云 DNS
- **IPv4/IPv6 双栈**：支持 A 记录（IPv4）和 AAAA 记录（IPv6）
- **定时更新**：支持自定义更新间隔的定时 DDNS 任务
- **智能更新**：仅在 IP 变化时更新 DNS 记录，避免无效请求

### 系统功能
- **Web 管理界面**：现代化的响应式 Web 控制台
- **登录认证**：密码保护，支持"保持登录"功能
- **操作日志**：记录所有 DDNS 操作和系统事件
- **多账号管理**：支持管理多个云服务商账号

## 技术栈

- **后端**：Spring Boot 3.5.0 + Java 21
- **前端**：Thymeleaf + 原生 JavaScript + Font Awesome
- **云 SDK**：
  - 腾讯云 SDK `tencentcloud-sdk-java:3.1.1194`
  - 阿里云 SDK `alidns20150109:3.0.8`
- **构建工具**：Gradle 8.5

## 项目结构

```
PublicIPUpload/
├── build.gradle                 # Gradle 构建配置
├── src/main/
│   ├── java/top/hanlin/publicipupload/
│   │   ├── PublicIpUploadApplication.java  # Spring Boot 启动类
│   │   ├── config/
│   │   │   ├── LoginInterceptor.java       # 登录拦截器
│   │   │   ├── WebConfig.java              # Web 配置
│   │   │   └── GlobalExceptionHandler.java # 全局异常处理
│   │   ├── controller/
│   │   │   ├── RepostController.java       # 页面路由控制器
│   │   │   └── TencentApiController.java   # API 接口控制器
│   │   ├── entity/
│   │   │   ├── DdnsTask.java               # DDNS 任务实体
│   │   │   └── UserInfo.java               # 用户信息实体
│   │   ├── service/
│   │   │   ├── DdnsTaskService.java        # DDNS 任务服务
│   │   │   ├── RepostService.java          # 用户服务接口
│   │   │   └── TencentApiService.java      # 云 API 服务接口
│   │   └── util/
│   │       ├── DDNS.java                   # DDNS 核心工具类
│   │       └── IP_SERVICES.java            # IP 服务枚举
│   └── resources/
│       ├── application.properties          # 应用配置
│       ├── static/
│       │   ├── css/                        # 样式文件
│       │   └── js/                         # JavaScript 文件
│       └── templates/
│           ├── index.html                  # 登录页面
│           └── pages/console.html          # 控制台页面
├── ip_services.txt              # 自定义 IP 服务配置（运行时生成）
├── local_interfaces.txt         # 本地网卡监控配置（运行时生成）
└── log/app.log                  # 应用日志（运行时生成）
```

## 快速开始

### 环境要求
- JDK 21 或更高版本
- Gradle 8.5 或更高版本

### 运行项目

```bash
# 克隆项目
git clone https://github.com/Mo-Manke/PublicIPUpload.git
cd PublicIPUpload

# 构建并运行
./gradlew bootRun

# 或者打包后运行
./gradlew bootJar
java -jar build/libs/PublicIpUpload-*.jar
```

### 访问系统

启动后访问 `http://localhost:8080`，使用配置的密码登录。

默认密码在 `application.properties` 中配置：
```properties
password=your_password
```

## 使用说明

### 1. 添加云服务账号
1. 登录控制台
2. 在"云服务账号"区域点击"添加账号"
3. 选择云服务商（腾讯云/阿里云）
4. 输入 SecretId 和 SecretKey
5. 点击保存

### 2. 创建 DDNS 任务
1. 选择已添加的云服务账号
2. 选择要更新的域名和解析记录
3. 选择 IP 获取服务（在线服务或本地网卡）
4. 设置更新间隔（分钟）
5. 点击"创建任务"

### 3. 添加自定义 IP 服务
1. 在 IPv4 或 IPv6 区域点击"添加服务"
2. 选择服务类型：
   - **在线 URL**：输入返回纯 IP 文本的 URL
   - **本地网卡**：选择要监控的本地网络接口

### 4. 管理 DDNS 任务
- **启用/禁用**：点击任务卡片上的开关
- **编辑**：点击编辑按钮修改任务配置
- **删除**：点击删除按钮移除任务
- **立即执行**：点击执行按钮立即更新 DNS

## 配置说明

### application.properties

```properties
# 服务端口
server.port=8080

# 登录密码
password=your_secure_password

# 日志配置
logging.file.name=log/app.log
logging.level.root=INFO
```

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/dns/allIps` | GET | 获取所有 IP 服务结果 |
| `/api/dns/ipv4` | GET | 仅获取 IPv4 服务结果 |
| `/api/dns/ipv6` | GET | 仅获取 IPv6 服务结果 |
| `/api/dns/networkInterfaces` | GET | 获取本地网卡列表 |
| `/api/dns/tasks` | GET/POST | 获取/创建 DDNS 任务 |
| `/api/dns/domainList` | POST | 获取域名列表 |
| `/api/dns/logs` | GET | 获取操作日志 |

## 注意事项

1. **安全性**：请妥善保管云服务商的 SecretId 和 SecretKey，不要泄露
2. **网络要求**：确保服务器能够访问云服务商 API 和 IP 获取服务
3. **更新间隔**：建议设置合理的更新间隔（如 5-10 分钟），避免频繁请求
4. **IPv6 支持**：IPv6 功能需要服务器具有 IPv6 网络环境

## 贡献者

- 项目由 [Mo-Manke](https://github.com/Mo-Manke) 开发和维护

## 许可证

本项目遵循 MIT 许可证。

## 问题反馈

如有问题或建议，请通过 [GitHub Issues](https://github.com/Mo-Manke/PublicIPUpload/issues) 提交。