# 苍穹外卖 (Sky Take-Out) — 改进版

基于 [苍穹外卖](https://github.com/shiyandgit/sky-take-out) 原版项目的全面安全加固与质量优化版本。

## 技术栈

- **后端框架**: Spring Boot 2.7.3
- **ORM**: MyBatis + PageHelper
- **数据库**: MySQL + Druid 连接池
- **缓存**: Redis
- **认证**: JWT (双拦截器：管理端/用户端)
- **支付**: 微信支付 v3
- **存储**: 阿里云 OSS
- **AI**: 通义千问 (Spring AI)
- **实时通信**: WebSocket
- **API 文档**: Knife4j (Swagger)

## 与原版的主要改进

### Phase 1: 安全加固

| 改进项 | 说明 |
|--------|------|
| **凭据外置** | 所有敏感配置（数据库密码、Redis密码、微信密钥、JWT密钥、OSS密钥）改为环境变量注入，不再硬编码 |
| **.gitignore** | 新增 `.gitignore`，排除 `target/`、`*.log`、`.idea/`、`.env`、`*.pem` 等敏感文件 |
| **JWT 密钥增强** | `admin-secret-key` 和 `user-secret-key` 改为环境变量占位符，生产环境必须设置 |
| **日志脱敏** | 拦截器日志中的 JWT Token 截断显示（只显示前10位），防止完整 Token 泄露 |
| **ThreadLocal 泄漏修复** | 两个拦截器的 `afterCompletion` 方法中添加 `BaseContext.removeCurrentId()`，防止线程复用导致用户 ID 串号 |
| **BaseContext 可见性** | `threadLocal` 字段从 `public` 改为 `private static final` |

### Phase 2: 代码质量

| 改进项 | 说明 |
|--------|------|
| **包名修正** | `controller/nofity/` → `controller/notify/`（修复拼写错误） |
| **异常处理增强** | `GlobalExceptionHandler` 新增 `MethodArgumentNotValidException`、`HttpMessageNotReadableException`、`HttpRequestMethodNotSupportedException`、通用 `Exception` 四个处理器 |
| **参数验证** | 添加 `spring-boot-starter-validation` 依赖，DTO 类添加 `@NotBlank`、`@NotNull`、`@Pattern` 注解，Controller 方法添加 `@Valid` |
| **泛型修复** | `PageResult.records` 从 `List` 改为 `List<?>`；`OrderServiceImpl`、`ReportServiceImpl`、`WorkspaceServiceImpl` 中的 `Map`/`List` 声明添加泛型 |
| **日志规范化** | `AutoFillAspect`、`WeChatPayUtil`、`HttpClientUtil`、`WebSocketServer` 中的 `e.printStackTrace()` / `System.out.println` 替换为 `log.error()` |
| **JSON 库统一** | `PayNotifyController`、`WeChatPayUtil`、`HttpClientUtil` 中的 Fastjson 替换为 Jackson |
| **无用 import 清理** | 移除 `ShoppingCartServiceImpl` 中未使用的 `BeanContext` import |

### Phase 3: 架构改进

| 改进项 | 说明 |
|--------|------|
| **Redis SCAN 替代 KEYS** | `DishController.cleanCache()` 使用 `ScanOptions` + `Cursor` 替代 `KEYS` 命令，避免生产环境阻塞 Redis |
| **菜品缓存 TTL** | 用户端菜品缓存添加 60 分钟过期时间，防止缓存永久驻留 |
| **订单任务修复** | `OrderTask.processDeliveryOrder()` 改用 `deliveryTime` 查询，添加 `@Transactional` 保证原子性 |
| **购物车事务** | `ShoppingCartServiceImpl.addShoppingCart()` 添加 `@Transactional` |
| **支付回调事务** | `OrderServiceImpl.paySuccess()` 添加 `@Transactional`，确保支付状态更新的原子性 |

## 环境要求

- JDK 11 或 17（**注意**: 不支持 JDK 26，Lombok 与 JDK 26 存在兼容性问题）
- MySQL 8.x
- Redis 6.x+
- Maven 3.6+

## 快速启动

```bash
# 1. 克隆项目
git clone https://github.com/shiyandgit/sky-take-out.git
cd sky-take-out

# 2. 设置环境变量（可选，不设置则使用 application-dev.yml 中的默认值）
export DB_HOST=localhost
export DB_PASSWORD=your_password
export REDIS_PASSWORD=your_redis_password
export JWT_ADMIN_SECRET=your_jwt_secret_at_least_32_chars
export JWT_USER_SECRET=your_jwt_secret_at_least_32_chars

# 3. 编译运行
mvn clean package -DskipTests
java -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar
```

## 项目结构

```
sky-take-out/
├── sky-common/     # 公共工具类、异常定义、常量
├── sky-pojo/       # 实体类、DTO、VO
├── sky-server/     # 业务逻辑、控制器、配置
│   ├── src/main/java/com/sky/
│   │   ├── controller/    # REST 接口
│   │   ├── service/       # 业务逻辑
│   │   ├── mapper/        # MyBatis 映射
│   │   ├── config/        # 配置类
│   │   ├── interceptor/   # JWT 拦截器
│   │   ├── aspect/        # AOP 切面
│   │   └── websocket/     # WebSocket 服务
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-dev.yml
│       └── mapper/        # MyBatis XML
└── pom.xml
```

## 许可证

本项目仅供学习交流使用。
